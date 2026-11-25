#!/usr/bin/env python3
"""PreToolUse guard for the gauntlet (Claude Code hook).

Keeps agents from changing their own constraints:
  1. nobody edits the gauntlet itself (gauntlet/, .claude/, CLAUDE.md, the CI workflows), what the other agent tools
     read as their instructions, roles, settings and hooks (AGENTS.md, .codex/, .gemini/, .agents/, .grok/,
     .kimi-code/, .qwen/, .mcp.json, ...) or the user-level configuration of the agent tools (~/.claude/, ~/.codex/,
     ~/.gemini/, ~/.agents/, ~/.grok/, ~/.kimi-code/, ~/.qwen/, ~/.env, ...), which could switch the hooks off;
  2. only the `specifier` subagent writes the Gherkin specification, and once the specification is
     committed on the branch (= approved by the human in the chat) it is frozen for every agent;
  3. role scopes: specifier -> specs only, hardener -> test code only, reviewer and checker -> read-only;
  4. human-only actions are refused: merging the PR, rewriting or re-pointing the branch (--amend,
     rebase, force push, reset/checkout -B/branch -f to another revision), pushing to the base branch,
     --no-verify, core.hooksPath, git aliases and config includes (they hide any of the above), switching
     the hooks, the CI gate or the branch protection off.

Shell commands are checked heuristically: every path-like token of a command that writes to the working
tree is resolved (following `cd` and `git -C`, expanding globs) and compared with the protected paths; a git
switch or checkout without a pathspec is judged by the files it changes (the difference to the commit it moves to).
A determined agent could still find a way; the integrity check, CI and the human's PR review are the
backstop. The hook fails open on malformed input so a broken hook never bricks a session.

Maintenance without the guard (humans only): set GAUNTLET_GUARD=off in the environment the session
starts with - locally `GAUNTLET_GUARD=off claude`, in the cloud an environment variable of a
separate "admin" cloud environment (see gauntlet/README.md).
"""
import glob
import json
import os
import re
import shlex
import signal
import subprocess
import sys

HUMAN_OWNED = ("gauntlet/", ".claude/", "CLAUDE.md", ".github/workflows/gauntlet.yml",
               ".github/workflows/gauntlet-kit.yml", ".github/CODEOWNERS", ".gitlab/CODEOWNERS", "CODEOWNERS", ".git/",
               # what the other agent tools read as their instructions, roles, settings, hooks, rules and skills
               # (gauntlet/subagents.md, section 6): Codex, Antigravity, Grok Build, Kimi Code (agents.md is AGENTS.md in
               # another letter case), Qwen Code with the ignore files that decide what its tools see, and the
               # project's MCP servers, whose commands Kimi Code (in a trusted folder) and Claude Code (once approved)
               # start with a session, outside any tool hook
               "AGENTS.md", "AGENTS.override.md", ".codex/", "GEMINI.md", ".gemini/", ".agents/", ".grok/",
               ".kimi-code/", "QWEN.md", ".qwen/", ".qwenignore", ".agentignore", ".aiignore", ".mcp.json")
PROPERTIES = "gauntlet/config/gauntlet.properties"
DEFAULT_FEATURES = "src/acceptanceTest/resources/features"
DEFAULT_BASE = "origin/main"
TEST_CODE = ("src/test/", "src/acceptanceTest/java/")
GIT_VALUE_OPTIONS = {"-m", "-F", "-C", "-c", "-t", "--message", "--file", "--author", "--date", "--template",
                     "--reuse-message", "--reedit-message", "--fixup", "--squash", "--trailer"}
HOOK_SWITCH = re.compile(r"(?:^|[\s;&|(])(?:(?:export|unset)\s+GAUNTLET_(?:GUARD|STOP_HOOK)\b|GAUNTLET_(?:GUARD|STOP_HOOK)=)")
QUOTED = re.compile(r"'[^']*'|\"(?:\\.|[^\"\\])*\"")
MUTATING_COMMANDS = {"mv", "cp", "rm", "rmdir", "truncate", "tee", "ln", "chmod", "chown", "touch", "dd",
                     "install", "patch", "unzip", "tar", "rsync", "vi", "vim", "nvim", "nano", "emacs", "ed", "ex",
                     "mkdir", "shred"}
# git subcommands that rewrite files in the working tree (checked against protected paths) ...
GIT_WORKTREE_WRITING = {"checkout", "switch", "restore", "rm", "mv", "apply", "stash", "reset", "clean", "am",
                        "cherry-pick", "revert", "merge", "rebase", "pull"}
# ... and the ones that only change git state (forbidden to the read-only reviewer and checker, fine for others). The
# plumbing changes it the same way (git's documentation, 2.43): update-index and read-tree write the index,
# symbolic-ref a ref, hash-object -w, mktree, mktag, write-tree and commit-tree objects, and checkout-index,
# merge-file, sparse-checkout and bisect the files; the forms that only print are in plumbing_read_only
GIT_STATE_CHANGING = GIT_WORKTREE_WRITING | {"commit", "add", "push", "tag", "branch", "update-ref", "replace",
                                             "filter-branch", "filter-repo", "notes", "worktree", "submodule",
                                             "update-index", "read-tree", "write-tree", "symbolic-ref", "hash-object",
                                             "mktree", "mktag", "commit-tree", "checkout-index", "merge-file",
                                             "sparse-checkout", "bisect"}
GIT_READ_ONLY_FORMS = {"branch": ("--show-current", "--list", "--all", "--remotes", "--verbose", "--contains",
                                  "--no-contains", "--merged", "--no-merged", "--points-at", "--format", "--sort",
                                  "--color", "--no-color", "--column", "--no-column", "--ignore-case", "--abbrev",
                                  "--no-abbrev", "--omit-empty"),
                       "stash": ("list", "show"),
                       "tag": ("--list", "--contains", "--no-contains", "--merged", "--no-merged", "--points-at",
                               "--format", "--sort", "--color", "--no-color", "--column", "--no-column", "--ignore-case"),
                       "worktree": ("list",), "notes": ("list", "show"), "submodule": ("status",)}
HISTORY_REWRITING = {"rebase", "filter-branch", "filter-repo", "update-ref", "replace", "revert"}
# global git options whose value is the NEXT argument: `git --namespace x push --force` still runs push
GIT_GLOBAL_VALUE_OPTIONS = ("-C", "-c", "--git-dir", "--work-tree", "--namespace", "--config-env", "--attr-source",
                            "--super-prefix")
# `git config` options that take a value as the next argument, with the shortest prefix git resolves to them
GIT_CONFIG_VALUE_OPTIONS = (("--file", 5), ("--blob", 4), ("--type", 3), ("--default", 3), ("--comment", 9),
                            ("--value", 7), ("--url", 5))
# listing options of `git branch` whose value may be the next argument, with the shortest prefix git resolves
BRANCH_VALUE_OPTIONS = (("--sort", 4), ("--format", 6), ("--contains", 5), ("--no-contains", 8), ("--merged", 4),
                        ("--no-merged", 7), ("--points-at", 3))
CI_SWITCH = "Switching the CI gate or the branch protection off is reserved for the human."
# options of `gh api` that take a value: the word after them is not the endpoint
GH_API_VALUE_OPTIONS = ("-X", "--method", "-f", "--raw-field", "-F", "--field", "-H", "--header", "--input", "-q",
                        "--jq", "-t", "--template", "--hostname", "-p", "--preview", "--cache")
GH_MERGE_MUTATION = re.compile(r"\b(?:mergePullRequest|enablePullRequestAutoMerge|mergeBranch|enqueuePullRequest)\s*\(")
GH_PROTECTION_MUTATION = re.compile(r"\b(?:create|update|delete)(?:BranchProtectionRule|RepositoryRuleset)\s*\(")
HOOK_RUNNING = {"commit", "push", "merge", "pull", "am", "rebase", "cherry-pick", "revert"}   # --no-verify skips their hooks
SHELLS = {"bash", "sh", "zsh", "dash", "ksh", "mksh", "ash"}                # `-c` may sit in a cluster: bash -lc '...'
INTERPRETERS = {"python", "python3", "node", "ruby"} | SHELLS              # with -c / -e inline code
SHELL_LONG_VALUE_OPTIONS = ("--rcfile", "--init-file")
WRAPPERS = {"sudo", "command", "exec", "nohup", "time", "env", "timeout", "nice", "setsid", "stdbuf", "xargs"}
# options of wrappers that take a separate value: skipped together with the value so the wrapped command surfaces
WRAPPER_VALUE_OPTS = {
    "sudo": {"-u", "-g", "-p", "-r", "-t", "-C", "-U", "-h", "--user", "--group", "--prompt", "--role",
             "--type", "--close-from", "--other-user", "--host"},
    "env": {"-u", "-C", "-S", "--unset", "--chdir", "--split-string"},
    "exec": {"-a", "--argv0"},
    "time": {"-o", "-f", "--output", "--format"},
    "command": set(), "nohup": set(), "setsid": set(),
    "timeout": {"-s", "--signal", "-k", "--kill-after"},
    "nice": {"-n", "--adjustment"},
    "stdbuf": {"-i", "-o", "-e", "--input", "--output", "--error"},
    "xargs": {"-n", "-P", "-L", "-I", "-d", "-E", "-s", "-a", "--max-args", "--max-procs", "--max-lines",
              "--delimiter", "--eof", "--max-chars", "--arg-file", "--process-slot-var"},
}
SHELL_KEYWORDS = {"if", "then", "else", "elif", "do", "while", "until", "!", "{"}   # `then rm x` runs rm
GRADLE = {"gradlew", "gradle"}
GRADLE_PATH_OPTIONS = {"-p", "--project-dir", "-g", "--gradle-user-home", "--project-cache-dir", "-b", "--build-file",
                       "-c", "--settings-file", "-I", "--init-script", "--include-build"}
PERL_WRITES = re.compile(r"\b(open|unlink|rename|system|exec|qx|readpipe|mkdir|rmdir|truncate|chmod|chown|symlink|"
                         r"link|utime|syswrite|sysopen)\b|`")
# a perl switch cluster that ends in -e / -E: the program is the rest of the word or the next word (-ne, -lane, -0777ne)
PERL_PROGRAM_SWITCH = re.compile(r"(-[acdnpsStTuUwWXl0-9]*[eE])(.*)", re.S)
# -i edits in place (-pi, -i.bak, -wpi, a lone -i anywhere); M m I F C D V e E take the rest of their word, so the i
# of -Mstrict, -I/usr/lib or of an attached program (-neprint if ...) is none
PERL_IN_PLACE = re.compile(r"-(?!-)[^MmIFCDVeE]*i")
PERL_QUOTE_LIKE = {"q", "qq", "qw", "qx", "m", "qr", "s", "tr", "y"}
# perl expects a term after these, so a / that follows them opens a pattern (checked against perl 5.38)
PERL_BEFORE_PATTERN = {"", "(", ",", "!", "&&", "||", "=~", "!~", "{", ";", "if", "unless", "and", "or", "not",
                       "split", "grep", "for", "foreach", "while", "until"}
PERL_BRACKETS = {"(": ")", "[": "]", "{": "}", "<": ">"}
PERL_WORD = re.compile(r"[A-Za-z_]\w*(?:::\w+)*")
PERL_NUMBER = re.compile(r"\d[\d_]*(?:\.[\d_]+)?(?:[eE][+-]?\d+)?")
PERL_VARIABLE = re.compile(r"\$(?:#?[A-Za-z_]\w*(?:::\w+)*|\d+|[&.,;!@/\\+-])|@(?:[A-Za-z_]\w*(?:::\w+)*|[-+])")
PERL_OPERATOR = re.compile(r"&&|\|\||=~|!~|==|!=|>=|=>|->|\.\.\.?|\+\+|--|\*\*|::|[-+*/%&.=!~^|\\(){}\[\],;:>]")
PERL_FLAGS = re.compile(r"[A-Za-z]*")                  # the modifiers after a pattern or a quote-like text
PERL_READLINE = re.compile(r"<\$?\w*>")                # <>, <STDIN>, <$fh>
# what interpolating a pattern can run: (?{ }), (??{ }), (*{ }), @{[ ]}, ${\ }, $x{...}, $x[...]; a $ before ), | or
# the end is an anchor
PERL_PATTERN_CODE = re.compile(r"\(\s*[?*]\s*\??\s*\{|@|\$(?![)|]|\Z)")
# a redirection that writes a file: > >> >| &> &>>, <> (opens it for writing too) and >& - whose word may also be a
# file descriptor (`>&2` duplicates one, `>&-` closes it, `>&log` writes the file log)
REDIRECT = re.compile(r"(?<![<>])(<>|>&|>>?\|?)")
FD_WORD = re.compile(r"\d+-?|-")
# the descriptor written right before >& (`2>&word`, `{fd}>&word`): bash duplicates the word into it, and a word that is
# no number is an error there ("ambiguous redirect"), not a file. Only `>&word` and `1>&word` write the file `word`.
DUP_FD = re.compile(r"(?:^|(?<=[\s;&|(){}]))(\d+|\{[A-Za-z_][A-Za-z0-9_]*\})$")
# A command the guard cannot read in time or at all is refused, not let through: a hook that crashes or exceeds
# Claude Code's timeout lets the command run. 300 nested $( ... ) took a minute to read and 1000 overflowed the stack;
# a command written by hand nests a few levels, and 32 are read in a fraction of a second.
MAX_NESTING = 32
JUDGE_SECONDS = 20
TOO_COMPLEX = ("The guard cannot judge this command ({}). Write it as several simpler commands; if this is "
               "really needed, ask the human.")
GLOB_CHARS = re.compile(r"[*?\[]")
STATEMENT_END = re.compile(r";|&&|\|\||\n")


def main():
    if os.environ.get("GAUNTLET_GUARD", "").lower() == "off":
        return 0
    try:
        event = json.load(sys.stdin)
    except Exception:
        return 0
    project = os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or os.getcwd()
    agent = event.get("agent_type") or "main"
    tool = event.get("tool_name", "")
    params = event.get("tool_input") or {}
    ctx = Context(project)
    if tool in ("Edit", "Write", "MultiEdit", "NotebookEdit"):
        path = params.get("file_path") or params.get("notebook_path") or ""
        reason = (user_config_reason(path, (project, event.get("cwd")))
                  or path_violation(agent, ctx.relative(path), ctx))
    elif tool == "Bash":
        reason = bash_violation(agent, params.get("command", ""), ctx, event.get("cwd") or project)
    else:
        reason = None
    if reason:
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": "[gauntlet guard] " + reason}}))
    return 0


class JudgeTimeout(BaseException):
    """The reading ran out of time. A BaseException, so that no `except Exception` on the way swallows it."""


def nesting_depth(command):
    """How deep $( ... ), <( ... ) and >( ... ) nest, counted on the raw text without regard to quotes. It refuses an
    absurd command early; what it miscounts is caught by the time budget and the recursion limit in bash_violation."""
    depth = deepest = 0
    for i, c in enumerate(command):
        if c == "(" and i and command[i - 1] in "$<>":
            depth += 1
            deepest = max(deepest, depth)
        elif c == ")" and depth:
            depth -= 1
    return deepest


def bash_violation(agent, command, ctx, cwd):
    """command_violation, refused instead of crashing or running out of time."""
    depth = nesting_depth(command)
    if depth > MAX_NESTING:
        return TOO_COMPLEX.format(f"$( ... ) nested {depth} levels deep")
    timer = hasattr(signal, "SIGALRM")
    if timer:
        def expired(signum, frame):
            raise JudgeTimeout
        previous = signal.signal(signal.SIGALRM, expired)
        signal.alarm(JUDGE_SECONDS)
    try:
        return command_violation(agent, command, ctx, cwd)
    except RecursionError:
        return TOO_COMPLEX.format("it nests too deeply to read")
    except JudgeTimeout:
        return TOO_COMPLEX.format(f"reading it took longer than {JUDGE_SECONDS} s")
    finally:
        if timer:
            signal.alarm(0)
            signal.signal(signal.SIGALRM, previous)


def features_rel_of(value):
    """acceptance.features as gauntlet.gradle reads it: an empty value is the default, and "./src/.../features/"
    names the directory "src/.../features" - as a text prefix it named no path, and the spec was not frozen."""
    value = (value or "").strip() or DEFAULT_FEATURES
    return os.path.normpath(value).replace(os.sep, "/").strip("/")


def module_dirs_of(value):
    """module as CI reads it: comma-separated build directories, every entry stripped of blanks and slashes and
    read as a path - ./core, core/. and core/ are core, . and ./ the build at the repository root. As a text
    prefix ./core named no path: the main agent could write the module's spec, the specifier and hardener nothing."""
    dirs = []
    for entry in (value or "").split(","):
        d = os.path.normpath(entry.strip()).replace(os.sep, "/").strip("/") if entry.strip() else ""
        if d and d not in dirs:
            dirs.append(d)
    return [] if dirs == ["."] else dirs


class Context:
    """Where the specification lives, which branch is the base, and whether the spec is frozen."""

    def __init__(self, project):
        self.project = project
        props = read_properties(os.path.join(project, PROPERTIES))
        # module = the Gradle build directories inside a monorepo, comma-separated ("" = the repository root)
        self.modules = module_dirs_of(props.get("module"))
        prefixes = ["" if m == "." else m + "/" for m in self.modules] or [""]
        features_rel = features_rel_of(props.get("acceptance.features")) + "/"
        self.features_dirs = tuple(pfx + features_rel for pfx in prefixes)
        self.test_code = tuple(pfx + p for pfx in prefixes for p in TEST_CODE)
        self.src_main = [pfx + "src/main" for pfx in prefixes]
        self.build_dirs = tuple(pfx + d for pfx in prefixes for d in ("build/", ".gradle/")) + ("build/", ".gradle/")
        self.base_ref = props.get("baseRef", DEFAULT_BASE)
        self.base_branch = self.base_ref.split("/", 1)[1] if "/" in self.base_ref else self.base_ref
        self.approval = props.get("spec.approval", "chat").lower()
        self._frozen = None
        self._worktrees = None

    @property
    def frozen(self):
        """True once the branch holds a commit that touches the feature files and they differ from the
        base branch: the human approved the specification in the chat and the agent committed it. From
        then on only the human changes it. Once the base branch has the same feature files (the PR was
        merged) the branch is free again. Fails open (not frozen) when the base branch cannot be found."""
        if self._frozen is None:
            if self.approval == "pr":
                self._frozen = False    # spec.approval=pr: the human reads the spec in the PR, no freeze
            elif git_code(self.project, "symbolic-ref", "-q", "HEAD") != 0:
                self._frozen = True     # detached HEAD: not the task's branch, nothing may be specified here
            else:
                specs = [d.rstrip("/") for d in self.features_dirs]   # one task per branch: any module's spec freezes all
                count = git(self.project, "rev-list", "--count", f"{self.base_ref}..HEAD", "--", *specs)
                differs = git_code(self.project, "diff", "--quiet", self.base_ref, "HEAD", "--", *specs) == 1
                self._frozen = count.isdigit() and int(count) > 0 and differs
        return self._frozen

    def relative(self, path, cwd=None):
        """The path relative to the project - or, for a file in ANOTHER worktree of the same repository
        (`git worktree add ../wt`), relative to that worktree: its gauntlet/, .claude/ and feature files are the
        same human-owned files in another checkout, and a commit made there lands in the same repository."""
        rel = relative(path, self.project, cwd)
        if rel is not None or not path:
            return rel
        if self._worktrees is None:
            listing = git(self.project, "worktree", "list", "--porcelain")
            self._worktrees = [os.path.realpath(line[len("worktree "):]) for line in listing.splitlines()
                               if line.startswith("worktree ")]
        for root in self._worktrees:
            rel = relative(path, root, cwd)
            if rel is not None:
                return rel
        return None

    def role_scope(self, agent):
        """Where a role may write: () is read-only - the reviewer and the checker, the subagent that runs the
        read-only cards of the core (code maps, framework checks, the critic, refuters); None is no role."""
        return {"specifier": self.features_dirs, "hardener": self.test_code, "reviewer": (), "checker": ()}.get(agent)

    def protected(self, agent):
        paths = [p.rstrip("/") for p in HUMAN_OWNED]
        if agent != "specifier" or self.frozen:
            paths += [d.rstrip("/") for d in self.features_dirs]
        if agent == "hardener":
            paths += self.src_main
        return paths

    def is_spec(self, path):
        return path in [d.rstrip("/") for d in self.features_dirs]


# ----------------------------------------------------------------------------- paths

def relative(path, project, cwd=None):
    if not path:
        return None
    base = cwd or project
    absolute = os.path.realpath(path if os.path.isabs(path) else os.path.join(base, path))
    rel = os.path.relpath(absolute, os.path.realpath(project)).replace(os.sep, "/")
    return None if rel.startswith("..") else rel


def is_under(rel, prefixes):
    r = rel.lower()
    return any(r == p.rstrip("/").lower() or r.startswith((p if p.endswith("/") else p + "/").lower()) for p in prefixes)


def contains(rel, prefixes):
    """The path is a parent of a protected path (rm -rf src takes the features with it)."""
    if rel == ".":
        return True
    r = rel.lower()
    return any(p.rstrip("/").lower().startswith(r + "/") for p in prefixes)


# The user's agent configuration outside the repository: settings, hooks, rules, instructions, agents and skills that
# an agent tool loads for every project. Whoever writes there can switch the hooks off for the next session or rewrite
# what an agent reads as its instructions. Each entry: the place - "~/..." or "/etc/...", or "$NAME" for the place an
# environment variable moves it to (the guard sees the environment Claude Code started with) -, whose it is, why it
# matters, and the subdirectories that are the tool's own session data: Kimi Code and Grok Build keep their sessions
# under their home and their agent writes its plan file there, and Grok Build runs a worktree session under
# worktrees/ (their documentation for the versions in gauntlet/subagents.md, section 5). The place itself and
# everything under it count, in any letter case.
SWITCH = "it could switch the hooks off"
USER_CONFIG = (
    ("~/.claude", "the user's Claude Code configuration", SWITCH, ()),
    ("~/.codex", "the user's Codex configuration", SWITCH, ()),
    ("$CODEX_HOME", "the user's Codex configuration", SWITCH, ()),
    ("/etc/codex", "the machine's Codex configuration", SWITCH, ()),
    ("~/.gemini", "the user's Gemini CLI and Antigravity configuration", SWITCH, ()),
    ("~/.agents", "the user's instructions, agents and skills of Codex, Antigravity and Kimi Code",
     "every session of theirs loads them", ()),
    ("~/.grok", "the user's Grok Build configuration", SWITCH, ("sessions", "worktrees")),
    ("$GROK_HOME", "the user's Grok Build configuration", SWITCH, ("sessions", "worktrees")),
    ("/etc/grok", "the machine's Grok Build configuration", SWITCH, ()),
    ("~/.kimi-code", "the user's Kimi Code configuration", SWITCH, ("sessions",)),
    ("$KIMI_CODE_HOME", "the user's Kimi Code configuration", SWITCH, ("sessions",)),
    ("~/.qwen", "the user's Qwen Code configuration", SWITCH, ()),
    ("$QWEN_HOME", "the user's Qwen Code configuration", SWITCH, ()),
    ("/etc/qwen-code", "the machine's Qwen Code configuration", SWITCH, ()),
    ("$QWEN_CODE_SYSTEM_SETTINGS_PATH", "the machine's Qwen Code configuration", SWITCH, ()),
    ("$QWEN_CODE_SYSTEM_DEFAULTS_PATH", "the machine's Qwen Code configuration", SWITCH, ()),
    ("$QWEN_CODE_HOOK_APPROVALS_PATH", "the user's Qwen Code configuration", SWITCH, ()),
    ("~/.env", "the user's environment file",
     "Qwen Code loads it into its environment, and the hooks get that environment: GAUNTLET_GUARD=off there switches "
     "the guard off", ()),
)
HOME_VARIABLE = re.compile(r"\$\{HOME\}|\$HOME(?!\w)")


def within(path, root):
    """path relative to root when it is root or lies under it, the names compared in any letter case; else None."""
    p, r = path.lower(), root.rstrip(os.sep).lower()
    if p == r:
        return "."
    return path[len(r) + 1:] if r and p.startswith(r + os.sep) else None


def user_config_place(entry):
    """(how the refusal names the place, its real path), or (None, None) when an environment variable is not set or
    names what the guard cannot hold as configuration: a relative path, or a directory that holds the home directory
    or the project (every file there would be the tool's)."""
    if not entry.startswith("$"):
        return entry, os.path.realpath(os.path.expanduser(entry))
    value = os.environ.get(entry[1:], "")
    expanded = os.path.expanduser(value)
    if not value or not os.path.isabs(expanded):
        return None, None
    place = os.path.realpath(expanded)
    for inside in (os.path.expanduser("~"), os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()):
        if within(os.path.realpath(inside), place) is not None:
            return None, None
    return f"{entry} ({value})", place


def user_config_violation(path):
    """The refusal for an absolute path in the user's agent configuration (USER_CONFIG), or None. A relative path is
    resolved by the caller: user_config_reason."""
    if not path:
        return None
    absolute = os.path.realpath(os.path.expanduser(path)) if os.path.isabs(os.path.expanduser(path)) else None
    if not absolute:
        return None
    for entry, whose, why, own_data in USER_CONFIG:
        shown, place = user_config_place(entry)
        inner = within(absolute, place) if place else None
        if inner is None or inner.split(os.sep, 1)[0].lower() in own_data:
            continue
        return f"{shown} is {whose} ({why}). Do not touch it."
    return None


def user_config_reason(path, cwds=()):
    """user_config_violation for the place a tool or a command writes: ~ and $HOME are the home directory (a quoted
    "$HOME/.env" too), and a relative path lies in each directory the command may be in - `cd ~ && echo x >
    .codex/config.toml` writes ~/.codex/config.toml."""
    if not path:
        return None
    home = os.path.expanduser("~")
    path = os.path.expanduser(HOME_VARIABLE.sub(lambda m: home, path))
    for candidate in [path] if os.path.isabs(path) else [os.path.join(c, path) for c in cwds if c]:
        reason = user_config_violation(candidate)
        if reason:
            return reason
    return None


def user_config_mention(text):
    """The refusal for the first place of the user's agent configuration the text names - ~/.env, $HOME/.env,
    /etc/codex, $GROK_HOME or the directory it holds, ... - for code and commands whose paths the hook cannot
    compute (inline code, a variable): the user-level twin of the text match against the protected paths."""
    home = os.path.expanduser("~")
    for entry, whose, why, own_data in USER_CONFIG:
        shown, place = user_config_place(entry)
        if not place:
            continue
        spellings = [re.escape(place), re.escape(entry)]
        if entry.startswith("~/"):
            spellings += [r"(?:~|\$HOME|\$\{HOME\}|" + re.escape(home) + ")" + re.escape(entry[1:])]
        elif entry.startswith("$"):
            spellings += [re.escape("${" + entry[1:] + "}")]
        pattern = r"(?<![\w.-])(?:" + "|".join(spellings) + r")(?![\w-]|\.\w)"
        if re.search(pattern, text, re.IGNORECASE):
            return f"{shown} is {whose} ({why}). Do not touch it."
    return None


def path_violation(agent, rel, ctx):
    if rel is None:
        return None
    if is_under(rel, HUMAN_OWNED):
        return (f"{rel} is part of the gauntlet and is human-owned. Do not change the checks; if one "
                f"looks wrong, stop and explain it to the human.")
    scope = ctx.role_scope(agent)
    if scope is not None:
        if not scope:
            return f"The {agent} is read-only. Report findings instead of editing files."
        if not is_under(rel, scope):
            return f"The {agent} may only write under {', '.join(scope)} (tried {rel})."
        if agent == "specifier" and ctx.frozen:
            return frozen_reason(rel)
        return None
    if is_under(rel, ctx.features_dirs):
        if ctx.frozen:
            return frozen_reason(rel)
        return (f"{rel} is the specification. Only the specifier subagent writes it, and only the human "
                f"approves it (in the chat). Make the code satisfy the spec; if the spec looks wrong, stop and ask.")
    return None


def frozen_reason(rel):
    return (f"{rel} is the approved specification: it is already committed on this branch, so it is frozen. "
            f"Make the code satisfy it. If it is wrong or impossible, stop and tell the human - only the human "
            f"changes an approved spec (on GitHub, or in a session with GAUNTLET_GUARD=off).")


# ----------------------------------------------------------------------------- shell

def command_violation(agent, command, ctx, cwd=None):
    command = shell_code(command)           # no comments, no continuation lines, no heredoc bodies
    reason = human_only(command, ctx, cwd)
    if reason:
        return reason
    scope = ctx.role_scope(agent)
    read_only = scope is not None and not scope
    protected = ctx.protected(agent)
    cwd = cwd or ctx.project
    reason = nested_violation(agent, command, ctx, cwd)
    if reason:
        return reason
    # inside quotes these characters are text: a quoted payload on several lines stays one word of its carrier
    neutral = QUOTED.sub(lambda m: re.sub(r"[>;&|()`\n]", "_", m.group(0)), command)
    statements = STATEMENT_END.split(neutral)
    hidden = unexpanded_redirect(neutral) if read_only else None
    if hidden:
        return unexpanded_reason(agent, hidden)
    substituted = before_substitution(neutral) if read_only else set()
    for segment, words, cwd in walk(segments_scoped(neutral), cwd, ctx.project):
        for target in redirect_targets(segment, cwd, ctx.project):
            reason = write_violation(agent, target, cwd, ctx, protected, read_only, scope, "writing " + target)
            if reason:
                return reason
        raw = raw_of(segment, neutral, command)
        if read_only:
            hidden = hidden_write(words, segment in substituted)
            if hidden:
                return unexpanded_reason(agent, hidden)
            if changes_state(words, segment, cwd, ctx, raw):
                return f"The {agent} is read-only: no file or git state changes. Report findings instead."
        if not writes_worktree(words, segment, raw):
            continue
        judged, reason = switch_judgment(agent, words, command, cwd, ctx)
        if reason:
            return reason
        if judged:
            continue                    # what it writes is the difference judged above, whatever names it holds
        tokens = [] if inline_code(words) else path_tokens(words, cwd, ctx.project)
        for token in tokens:
            reason = write_violation(agent, token, cwd, ctx, protected, read_only, scope, "this command")
            if reason:
                return reason
        # the user's agent configuration has no text match below, and path_tokens keeps only the names it finds:
        # `cd ~ && cp /tmp/x .env` writes a ~/.env that does not exist yet
        for word in [] if inline_code(words) else written_words(words):
            reason = user_config_reason(expand(word, cwd, ctx.project), candidates(cwd, ctx.project))
            if reason:
                return reason
        # What the tokens cannot tell is matched as text. Inline code (`python -c ...`): the whole command. A
        # write whose path comes from a variable or after a cd the hook could not follow: the whole command
        # outside quotes - the value may be set anywhere in it. A write that names no path at all, takes its
        # paths from a pipe (`git pull`, `... | xargs rm`) or names a file that does not exist yet, which the
        # tokens leave out (`mv /tmp/x AGENTS.md`, `dd of=.mcp.json`): its own statement - a neighbour that only
        # reads gauntlet/ is not its target.
        if inline_code(words):
            text = command
        elif unresolvable(segment) or not known(cwd):
            text = without_gradle_tasks(QUOTED.sub("''", command))
        elif not tokens or XARGS.search(segment) or unseen(words, cwd):
            text = without_gradle_tasks(QUOTED.sub("''", next((s for s in statements if segment in s), command)))
        else:
            continue
        hit = next((p for p in protected if mentions(text, p)), None)
        if hit:
            return protected_reason(agent, hit, ctx)
        # the user's agent configuration where the written paths are not known (code, a variable, a pipe); the ones
        # that are known were judged above - `cp ~/.env .env.local` only reads ~/.env
        reason = user_config_mention(text) if inline_code(words) or unresolvable(segment) or not known(cwd) \
            or XARGS.search(segment) else None
        if reason:
            return reason
    return None


def unexpanded_reason(agent, word):
    return (f"The {agent} is read-only: outside the project it writes only where the guard reads the path as written, "
            f"and {word} holds a variable, a substitution or ~ whose value the same line may set. Write the full path "
            f"(/tmp/notes.txt, <clone>/src/...) or ./name after cd; else report findings instead.")


def redirect_targets(segment, cwd, project):
    """The files the redirections of a statement write, each as the word bash makes of its target: quotes and
    backslashes removed, the pieces joined, a glob matched against the files there. `> 'gauntlet'/config/x`,
    `> gaunt\\let/config/x`, `> gauntle?/config/x` and `> "$CLAUDE_PROJECT_DIR"/gauntlet/config/x` write into
    gauntlet/ like `> gauntlet/config/x` does; `>&2` and `2>&1` write no file. Any other variable stays as
    written: what it holds is the known limit of a static reading. After >& the word is a file the way bash takes
    it: a number or - is a descriptor (`>&2`, `2>&1`, `>&-`); after a descriptor other than 1 (`2>&"$saved_stderr"`,
    `{fd}>&...`) no word is a file; `>&word` and `1>&word` write the file `word`, a variable as well."""
    targets = []
    for m in REDIRECT.finditer(segment):
        word = HERE_WORD.match(segment, m.end()).group(1)
        if not word:
            continue                            # >(...) is a process substitution, judged as the code it runs
        try:
            target = "".join(shlex.split(word))
        except ValueError:
            target = word
        target = expand(target, cwd, project)
        if m.group(1) == ">&" and (FD_WORD.fullmatch(target) or into_descriptor(segment, m.start())):
            continue
        if GLOB_CHARS.search(target) and not unresolvable(target):
            bases = [""] if os.path.isabs(target) else candidates(cwd, project)
            targets.extend([p for base in bases for p in glob.glob(os.path.join(base, target))] or [target])
        else:
            targets.append(target)
    return targets


def into_descriptor(segment, at):
    """The >& at `at` duplicates into a descriptor other than 1, which takes no file name."""
    fd = DUP_FD.search(segment, 0, at)
    return fd is not None and (fd.group(1).startswith("{") or int(fd.group(1)) != 1)


def raw_of(segment, neutral, command):
    """The text as written behind a statement of the neutralised command line: quoted `...`, (...) and ; are
    perl's code (`perl -e 'system("rm -rf x")'`), not shell syntax. Neutralising keeps every offset; a statement
    whose text occurs more than once answers for all its occurrences, and one not found for the whole line."""
    if len(neutral) != len(command):
        return command
    parts, start = [], neutral.find(segment)
    while start >= 0 and segment:
        parts.append(command[start:start + len(segment)])
        start = neutral.find(segment, start + 1)
    return "\n".join(parts) if parts else command


def shell_payloads(words):
    """Shell text a command hands to another shell to run: `bash -lc '...'`, `su -c '...'`,
    `find ... -exec sh -c '...' ;`, `eval ...`. It is a command line like any other, so it is judged like one."""
    if not words:
        return []
    cmd, args = os.path.basename(words[0]), words[1:]
    if cmd in SHELLS:
        code, _ = shell_arguments(args)
        return [code] if code else []
    if cmd == "eval":
        return [" ".join(args)]
    if cmd in ("su", "runuser", "find"):
        return inline_code_strings(cmd, args)
    return []


def nested_violation(agent, command, ctx, cwd):
    """The path and role rules inside code that runs within the command: the payload of another shell, a program
    fed to a shell or an interpreter through a pipe, a here-string or a heredoc, and the bodies of command
    substitutions. `bash -lc 'rm -rf gauntlet'`, `echo 'rm -rf gauntlet' | sh`, `bash <<EOF ... EOF` and
    `echo "$(rm -rf gauntlet)"` write exactly like the bare command does - and `bash -lc 'cat gauntlet/README.md'`
    reads exactly like the bare command does."""
    end = []
    for segment, words, at in walk(split_shell(command), cwd, ctx.project, end):
        for code in shell_payloads(words) + stdin_programs(segment):
            reason = command_violation(agent, code, ctx, at)
            if reason:
                return reason
    for code in piped_shell_code(command) + command_substitutions(command):
        if code.strip() and code != command:
            reason = command_violation(agent, code, ctx, end[0])
            if reason:
                return reason
    return None


# ----------------------------------------------------------------------------- reading the command line

HEREDOCS = {}                               # the here-string word that stands for a heredoc -> (body, expanded)
HEREDOC_REF = "__GAUNTLET_HEREDOC_%d__"
HEREDOC = re.compile(r"""<<(-?)[ \t]*((?:'[^']*'|"[^"]*"|\\.|[^\s;&|()<>'"\\])+)""")
SHELL_WORD = r"""(?:'[^']*'|"(?:\\.|[^"\\])*"|\\.|[^\s;&|()<>'"\\])+"""
HERE_WORD = re.compile(r"[ \t]*(" + SHELL_WORD + r")?")
REDIRECTION = re.compile(r"[<>]{1,2}[|&]?[ \t]*(?:" + SHELL_WORD + r"|-)?")
RAW_WORD = re.compile(r"[^\s;&|()<>`]+")
WORD_BREAK = " \t\n;&|()<>`"
COMMAND_STARTERS = {"then", "do", "else", "elif", "if", "while", "until", "!", "time", "{"}
READ_AS_ONE = COMMAND_STARTERS | {"case", "esac", "in"}


class Frame:
    """One level of the syntax being read: the command line, $( ... ), ` ... `, ( ... ), a double-quoted
    string, or the body of a heredoc the shell expands."""

    def __init__(self, kind, arith=False):
        self.kind, self.arith = kind, arith     # arith: inside (( ... )), where << is a shift
        self.pending = []                       # heredocs whose bodies start after the next newline
        self.cases = []                         # open case statements, each "head", "pattern" or "body"
        self.fresh = True                       # no word of the current simple command has been read yet
        self.subject = False                    # the word between `case` and `in` has been read
        self.pattern = False                    # inside a case pattern: its opening parenthesis is written


def shell_code(command, kind="top"):
    """The command as the shell reads it, in the plain form the rules below can judge:
      - comments are gone (the apostrophe of `# don't` would open a quote that swallows the next command);
      - a backslash at the end of a line joins it with the next one, as the shell does before anything else;
      - a heredoc is the here-string `<<< __GAUNTLET_HEREDOC_n__` and its body is kept in HEREDOCS: the body
        is what the command reads on stdin - data for cat, a program for bash or python3 - and never a part
        of this command line, whatever quotes, `#` or `<<` it contains;
      - a case pattern is written `(: a b)`, an inert subshell, so that its closing parenthesis pairs with an
        opening one and is not taken for the end of a subshell or of $( ... ).
    Reading its own output changes nothing. If the reader fails, the rules judge the raw text."""
    try:
        return read_shell(command, kind)
    except Exception:
        return command


def arithmetic_at(command, i):
    """True when the `((` at i opens an arithmetic expression: its inner parenthesis closes right before the
    outer one. `((a); b)` is two subshells, and what stands in them are commands."""
    depth, quote, k, n = 0, None, i + 1, len(command)
    while k < n:
        c = command[k]
        if quote:
            if c == "\\" and quote == '"':
                k += 1
            elif c == quote:
                quote = None
        elif c == "\\":
            k += 1
        elif c in "'\"":
            quote = c
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return command[k + 1:k + 2] == ")"
        k += 1
    return False


def read_shell(command, kind):
    out, frames, i, n = [], [Frame(kind)], 0, len(command)

    def reader():                               # whose parser takes the heredoc bodies: the line, $( ) or ` `
        return next(f for f in reversed(frames) if f.kind in ("top", "here", "subst", "tick"))

    def close(k):                               # frames[k:] end here; unread heredocs pass to the enclosing reader
        pending = [h for frame in frames[k:] for h in frame.pending]
        del frames[k:]
        reader().pending.extend(pending)

    def begin_word(f):
        f.fresh = False
        if f.cases and f.cases[-1] == "head":
            f.subject = True

    while i < n:
        c, two, f = command[i], command[i:i + 2], frames[-1]
        prev = out[-1][-1] if out else "\n"
        if two == "\\\n":                        # a line continuation (single-quoted text is copied whole below)
            i += 2
        elif f.kind in ("dq", "here"):
            if c == "\\":
                out.append(two)
                i += 2
            elif c == '"' and f.kind == "dq":
                out.append(c)
                frames.pop()
                i += 1
            elif two == "$(":
                out.append(two)
                i += 2
                frames.append(Frame("subst", command.startswith("(", i) and arithmetic_at(command, i - 1)))
            elif c == "`":
                out.append(c)
                i += 1
                frames.append(Frame("tick"))
            else:
                out.append(c)
                i += 1
        elif c == "#" and prev in WORD_BREAK:     # a comment runs to the end of the line
            end = command.find("\n", i)
            i = n if end < 0 else end
        elif f.cases and f.cases[-1] == "pattern" and not f.pattern and c not in " \t\n":
            word = RAW_WORD.match(command, i)
            if word and word.group(0) == "esac":
                out.append("esac")
                i += 4
                f.cases.pop()
            else:
                out.append("(")
                if c == "(":                    # the optional parenthesis before a pattern
                    i += 1
                if not command[i:].lstrip(" \t").startswith(": "):
                    out.append(": ")
                f.pattern = True
        elif f.pattern and c == "|":
            out.append(" ")
            i += 1
        elif f.pattern and c == ")":
            out.append(c)
            i += 1
            f.cases[-1], f.pattern, f.fresh = "body", False, True
        elif c == "\\":
            out.append(two)
            i += 2
            begin_word(f)
        elif c == "'":
            end = command.find("'", i + 1)
            end = n if end < 0 else end + 1
            out.append(command[i:end])
            i = end
            begin_word(f)
        elif two == "$'":                      # ANSI-C string: the word bash makes of it, in plain quotes
            end = ansi_c_end(command, i + 2)
            if end > n:                        # never closed: bash runs nothing, the text stays as written
                out.append(command[i:])
                end = n
            else:
                out.append("'" + ansi_c_text(command[i + 2:end - 1]).replace("'", "'\\''") + "'")
            i = end
            begin_word(f)
        elif c == '"' or two == '$"':
            out.append('"')
            i += 2 if c == "$" else 1
            begin_word(f)
            frames.append(Frame("dq"))
        elif two == "$(":
            out.append(two)
            i += 2
            begin_word(f)
            frames.append(Frame("subst", command.startswith("(", i) and arithmetic_at(command, i - 1)))
        elif c == "`":
            out.append(c)
            i += 1
            opened = next(k for k in range(len(frames) - 1, -1, -1) if frames[k].kind != "paren")
            if frames[opened].kind == "tick":
                close(opened)
            else:
                begin_word(f)
                frames.append(Frame("tick"))
        elif c == "(":
            out.append(c)
            i += 1
            frames.append(Frame("paren", f.arith or (f.fresh and command.startswith("(", i) and arithmetic_at(command, i - 1))))
        elif c == ")":
            out.append(c)
            i += 1
            if f.kind in ("paren", "subst"):
                close(len(frames) - 1)
                if f.kind == "paren":
                    frames[-1].fresh = True
        elif f.arith and c in "<>&|;":           # operators of an arithmetic expression, not of the shell
            out.append(" ")
            i += 1
        elif c == "\n":
            out.append(c)
            i += 1
            f.fresh = not f.pattern
            pending = reader().pending
            if pending:
                i = read_heredocs(command, i, pending)
                del pending[:]
        elif two == "<<" and not f.arith:
            m = None if command.startswith("<<<", i) else HEREDOC.match(command, i)
            if m is None:
                out.append("<<<" if command.startswith("<<<", i) else two)
                i += 3 if command.startswith("<<<", i) else 2
            else:
                delimiter = "".join(shlex.split(m.group(2)))
                ref = HEREDOC_REF % len(HEREDOCS)
                HEREDOCS[ref] = ("", not re.search(r"['\"\\]", m.group(2)))
                reader().pending.append((ref, delimiter, bool(m.group(1))))
                out.append(" <<< " + ref + " ")
                i = m.end()
        elif c in ";&|":
            if f.cases and f.cases[-1] == "body" and c == ";" and command[i + 1:i + 2] in (";", "&"):
                end = i + 2 + (command[i + 2:i + 3] == "&")
                out.append(command[i:end])
                i = end
                f.cases[-1], f.pattern, f.fresh = "pattern", False, False
            else:
                out.append(c)
                i += 1
                f.fresh = True
        elif c in " \t<>":
            out.append(c)
            i += 1
        else:
            word = RAW_WORD.match(command, i).group(0) if prev in WORD_BREAK else ""
            if f.cases and f.cases[-1] == "head":
                if word == "in" and f.subject:
                    f.cases[-1], f.pattern = "pattern", False
                f.subject = True
            elif f.fresh and word == "case":
                f.cases.append("head")
                f.subject = False
            elif f.fresh and word == "esac" and f.cases:
                f.cases.pop()
            f.fresh = f.fresh and word in COMMAND_STARTERS
            if word == "{":
                f.fresh = True                  # `f() { case ...`: a group starts a command wherever it stands
            step = len(word) if word in READ_AS_ONE else 1
            out.append(command[i:i + step])
            i += step
    return "".join(out)


def read_heredocs(command, i, pending):
    """Take the bodies of the pending heredocs, which start at i, out of the command line: the index after them."""
    n = len(command)
    for ref, delimiter, strip_tabs in pending:
        lines = []
        while i < n:
            end = command.find("\n", i)
            end = n if end < 0 else end
            line = command[i:end].lstrip("\t") if strip_tabs else command[i:end]
            i = min(end + 1, n)
            if line == delimiter:
                break
            lines.append(line)
        HEREDOCS[ref] = ("\n".join(lines), HEREDOCS[ref][1])
    return i


def here_strings(segment):
    """(the arguments of the segment, [(text, expanded by the shell)]): the segment without its redirections and
    here-strings. `2>&1`, `>/dev/null`, `<file` say where the command reads and writes and `<<< word` is what it
    reads on stdin; none of them is an argument - `git push origin 2>&1` names no refspec, `bash 2>&1` no script.
    (What a redirection writes is judged separately, by redirect_targets.)"""
    if "<" not in segment and ">" not in segment:
        return segment, []
    out, texts, quote, i, n = [], [], None, 0, len(segment)
    while i < n:
        c = segment[i]
        if c == "\\" and quote != "'":
            out.append(segment[i:i + 2])
            i += 2
            continue
        if quote:
            quote = None if c == quote else quote
        elif segment.startswith("$'", i):
            end = min(ansi_c_end(segment, i + 2), n)
            out.append(segment[i:end])
            i = end
            continue
        elif c in "'\"":
            quote = c
        elif segment.startswith("<<<", i):
            m = HERE_WORD.match(segment, i + 3)
            word = m.group(1) or ""
            try:
                text = "".join(shlex.split(word))
            except ValueError:
                text = word
            texts.append(HEREDOCS.get(text, (text, False)))
            out.append(" ")
            i = m.end()
            continue
        elif c in "<>" and segment[i + 1:i + 2] != "(":
            k = len(out)
            while k and out[k - 1].isdigit():
                k -= 1
            if k < len(out) and (k == 0 or out[k - 1] in " \t"):
                del out[k:]                     # the file descriptor of 2>file
            elif out and out[-1] == "&":
                out.pop()                       # &>file
            out.append(" ")
            i = REDIRECTION.match(segment, i).end()
            continue
        out.append(c)
        i += 1
    return "".join(out), texts


def heredoc_substitutions(body):
    """Bodies of the command substitutions the shell runs while it expands a heredoc whose delimiter is not
    quoted: there quotes are plain text and only $( ... ) and ` ... ` are live."""
    body = shell_code(body, "here")
    bodies, i, n = [], 0, len(body)
    while i < n:
        if body[i] == "\\":
            i += 2
        elif body.startswith("$(", i):
            end = substitution_end(body, i + 2)
            bodies.append(body[i + 2:end - 1])
            i = end
        elif body[i] == "`":
            end = backtick_end(body, i + 1)
            bodies.append(re.sub(r"\\([`$\\])", r"\1", body[i + 1:end - 1]))
            i = end
        else:
            i += 1
    return bodies


def as_program(words, text):
    """The command that stands for `text` being the program of the shell or interpreter that `words` start:
    for a shell the text itself, for python / node / ruby / perl their -c / -e form."""
    cmd = os.path.basename(words[0])
    if cmd in SHELLS:
        return text
    return cmd + (" -c " if cmd == "python" or PYTHON_VERSIONED.match(cmd) else " -e ") + shlex.quote(text)


def stdin_programs(segment):
    """Commands that stand for what a stage is fed through a here-string or a heredoc. A shell without a script
    (`bash <<EOF`, `sh -s <<< "..."`) runs the text, python3 / node / ruby / perl without a script run it as
    their program; and whoever reads it, the shell itself first runs the substitutions of a heredoc body whose
    delimiter is not quoted."""
    rest, texts = here_strings(segment)
    if not texts:
        return []
    words = words_of(rest)
    codes = []
    for text, expanded in texts:
        if expanded:
            codes.extend(heredoc_substitutions(text))
        if reads_stdin_program(words):
            codes.append(as_program(words, text))
    return codes


XARGS = re.compile(r"(^|[\s|;&])xargs\s")               # its arguments come from the pipeline it stands in


def unresolvable(segment):
    return "$" in segment or "`" in segment


def expand(token, cwd, project):
    """The few variables whose value the hook knows; anything else stays literal and triggers text matching."""
    token = token.strip("'\"")
    for var in ("${CLAUDE_PROJECT_DIR}", "$CLAUDE_PROJECT_DIR"):
        token = token.replace(var, project)
    for var in ("${PWD}", "$PWD"):
        token = token.replace(var, cwd)
    return os.path.expanduser(token)


def write_violation(agent, target, cwd, ctx, protected, read_only, scope, what):
    target = expand(target, cwd, ctx.project)
    reason = user_config_reason(target, candidates(cwd, ctx.project))
    if reason:
        return reason
    # one directory when the hook followed the command there, every candidate after a cd it could not follow
    rels = [r for r in (ctx.relative(target, c) for c in candidates(cwd, ctx.project)) if r is not None]
    if not rels:
        return None
    if read_only:
        return f"The {agent} is read-only. Report findings instead of {what}."
    if all(is_under(rel, ctx.build_dirs) for rel in rels):
        return None                     # Gradle outputs: any role may clean or write them
    for rel in rels:
        hit = next((p for p in protected if is_under(rel, (p,)) or contains(rel, (p,))), None)
        if hit:
            return protected_reason(agent, hit, ctx)
    if scope and not any(is_under(rel, scope) or rel == "." for rel in rels):
        return f"The {agent} may only change files under {', '.join(scope)} ({what} touches {rels[0]})."
    return None


def protected_reason(agent, hit, ctx):
    if ctx.is_spec(hit) and ctx.frozen:
        return frozen_reason(hit)
    return (f"This command could modify {hit}, which the {agent} agent must not change. "
            f"Human-owned files and the specification change only through the human.")


def human_only(command, ctx, cwd=None):
    if cwd is None:
        cwd = ctx.project
    command = shell_code(command)
    if HOOK_SWITCH.search(QUOTED.sub("''", command)):
        return "Switching the gauntlet hooks off is reserved for the human."
    end = []
    for segment, words, at in walk(split_shell(command), cwd, ctx.project, end):   # `cd worktree && git push`
        for code in stdin_programs(segment):       # `bash <<EOF`, `python3 - <<EOF`: the body is the program
            reason = human_only(code, ctx, at)
            if reason:
                return reason
        if len(words) < 2:
            continue
        cmd, args = os.path.basename(words[0]), words[1:]
        if cmd == "git":
            reason = git_human_only(args, ctx, at)
            if reason:
                return reason
        elif cmd == "gh":
            reason = gh_human_only(args)
            if reason:
                return reason
        for code in inline_code_strings(cmd, args):
            reason = human_only(code, ctx, at)
            if reason:
                return reason
    for code in piped_shell_code(command) + command_substitutions(command):
        reason = human_only(code, ctx, end[0])
        if reason:
            return reason
    return None


PYTHON_VERSIONED = re.compile(r"^python[23](?:\.\d+)?$")
ARGV_EXEC = re.compile(r"(?:subprocess\.\w+|os\.exec\w*|os\.spawn\w*|os\.popen"
                       r"|\b(?:run|call|check_call|check_output|Popen|popen\w*|capture\w*|execv\w*|execFile\w*|spawn\w*)\b)\s*\(\s*"
                       r"(?:['\"]([^'\"]*)['\"]\s*,\s*)?"                     # execvp('git', [...]) / spawnSync('git', [...])
                       r"(\[\s*(?:['\"][^'\"]*['\"]\s*,?\s*)+\])")
# an exec-style call that takes the program and its arguments as separate strings: perl / ruby
# system("git", "push", "--force") and exec 'git', 'push'; python os.execlp('git', 'git', 'push'),
# os.spawnlp(os.P_WAIT, 'git', 'git', 'push'); ruby Open3.capture2("git", "push")
ARGS_EXEC = re.compile(r"(?<!\w)(?:system|exec\w*|spawn\w*|capture\w*|popen\w*)\s*\(?\s*(?:os\.\w+\s*,\s*)?"
                       r"((?:['\"][^'\"]*['\"]\s*,\s*)+['\"][^'\"]*['\"])")
# an exec-style call whose first argument is a shell string: os.system('git push --force'),
# subprocess.run('gh pr merge'), perl system("..."), node child_process.execSync('...'). A quoted string
# anywhere else (print('git rebase'), console.log('...'), regex.exec('...')) is data, not a command.
STRING_EXEC = re.compile(
        r"(?:os\.(?:system|popen|exec\w*|spawn\w*)|subprocess\.\w+|child_process\.\w+"
        r"|\b(?:execSync|execFileSync|spawnSync|execFile|shell_exec|passthru|proc_open|system|popen)"
        r"|(?<![.\w])(?:exec|spawn))\s*\(\s*")


def is_interpreter(cmd):
    """An interpreter that runs inline code with -c/-e, including a versioned python3.12 / python2.7."""
    return cmd in INTERPRETERS or PYTHON_VERSIONED.match(cmd) is not None


def argv_commands(codes):
    """`subprocess.run(['git','push','--force'])`: the argv list is one command - program argv[0] with its
    literal arguments. Quote each element so a DATA argument (`['echo', 'example; git rebase']` only prints
    text) is not re-split on shell metacharacters; only the program and its real flags reach the git/gh rules.
    Only an exec-style call qualifies, so a plain data list (`print(['git','push'])`) is not assembled."""
    out = []
    for code in codes:
        for m in ARGV_EXEC.finditer(code):
            parts = re.findall(r"['\"]([^'\"]*)['\"]", m.group(2))
            if m.group(1) and parts[:1] != [m.group(1)]:
                parts.insert(0, m.group(1))                # the program named before the argument list
            if parts:
                out.append(" ".join(shlex.quote(p) for p in parts))
        for m in ARGS_EXEC.finditer(code):
            parts = re.findall(r"['\"]([^'\"]*)['\"]", m.group(1))
            if os.path.basename(parts[0]) == parts[1]:
                parts.pop(0)                               # execlp(file, argv0, ...): the program is named twice
            out.append(" ".join(shlex.quote(p) for p in parts))
    return out


def shell_arguments(args):
    """How a shell is invoked: (the command string of -c or None, True when the program comes from stdin).
    Short options cluster - `bash -lc '...'`, `sh -ec '...'`, `bash -euo pipefail -c '...'` - and `-o name` /
    `-O name` take the next argument; the command string of -c is the FIRST OPERAND after all the options,
    not the word after the letter c."""
    has_c = has_s = False
    i = 0
    while i < len(args):
        a = args[i]
        if a in ("--", "-"):
            i += 1
            break
        if a.startswith("--"):
            i += 2 if a in SHELL_LONG_VALUE_OPTIONS else 1
            continue
        if len(a) > 1 and a[0] in "-+":
            if a[0] == "-":
                has_c = has_c or "c" in a[1:]
                has_s = has_s or "s" in a[1:]
            i += 2 if a[-1] in "oO" else 1          # -o pipefail, -O extglob, -euo pipefail: the name follows
            continue
        break
    operands = args[i:]
    if has_c:
        return (operands[0] if operands else None), False
    return None, has_s or not operands


def interpreter_option_value(cmd, args, index):
    if index == 0:
        return False
    previous = args[index - 1]
    if cmd == "python" or PYTHON_VERSIONED.match(cmd):
        return previous in ("-W", "-X", "--check-hash-based-pycs")
    if cmd == "node":
        return previous in ("-r", "--require", "--loader", "--import", "--inspect", "--inspect-brk",
                            "--inspect-port", "--conditions", "-C")
    if cmd == "ruby":
        return previous in ("-I", "-r", "-C", "--load-path", "--require")
    return False


# first letters of short options whose value is attached (-Werror, -Ilib, -rjson, -i.bak): not a flag cluster
ATTACHED_VALUE_OPTIONS = {"python": "WX", "node": "r", "ruby": "IrCEKTWx0F", "perl": "IMmxCDFV0i"}
CODE_FLAGS = {"python": "c", "node": "pe", "ruby": "e", "perl": "eE"}


def interpreter_programs(cmd, args):
    """Inline programs of python / node / ruby / perl. Each has its own flags and they cluster with others:
    python -c CODE (`python3 -Bc`), everything after it is argv, as after a script or a module; node -e / -p /
    --eval / --print CODE (`node -pe`), every one is read; ruby -e CODE and perl -e / -E CODE (`perl -we`,
    `perl -lE`, `ruby -ne`), every one runs."""
    kind = "python" if cmd == "python" or PYTHON_VERSIONED.match(cmd) else cmd
    flags, attached = CODE_FLAGS.get(kind, "e"), ATTACHED_VALUE_OPTIONS.get(kind, "")
    codes, i = [], 0
    while i < len(args):
        a = args[i]
        if kind != "perl":
            if a in ("--", "-"):
                break                       # stdin: the rest belongs to the application
            if kind == "python" and (a == "-m" or (a.startswith("-m") and not a.startswith("--"))):
                break                       # a module: the rest is its argv
            if not a.startswith("-"):
                if interpreter_option_value(cmd, args, i):
                    i += 1
                    continue
                break                       # the script file: option-looking words after it are its argv
        if kind == "node" and a.split("=", 1)[0] in ("--eval", "--print"):
            if "=" in a:
                codes.append(a.split("=", 1)[1])
            elif i + 1 < len(args):
                codes.append(args[i + 1])
                i += 1
        elif a.startswith("-") and not a.startswith("--") and len(a) > 1 and a[1] not in attached:
            position = next((k for k, flag in enumerate(a[1:], 1) if flag in flags), -1)
            if position > 0:
                rest = a[position + 1:]
                if rest and not all(flag in flags for flag in rest):
                    codes.append(rest)                      # attached: -c'code'; in -pe the e is one more flag
                elif i + 1 < len(args):
                    codes.append(args[i + 1])
                    i += 1
                if kind == "python":
                    break                   # after the program everything is argv data, not more code
        i += 1
    return codes


def inline_code_strings(cmd, args):
    """Code handed to an interpreter or eval: the human-only git/gh rules apply inside it too."""
    codes = []
    if cmd in SHELLS:
        code, _ = shell_arguments(args)
        if code is not None:
            codes.append(code)
    elif is_interpreter(cmd) or cmd == "perl":
        codes.extend(interpreter_programs(cmd, args))
        for code in list(codes):
            for m in STRING_EXEC.finditer(code):
                q = QUOTED.match(code, m.end())
                if q:
                    codes.append(q.group(0)[1:-1])   # the exec call's shell string: os.system('git push --force')
        codes.extend(argv_commands(codes))
    if cmd in ("eval", "source", "."):
        codes.append(" ".join(args))
    if cmd in ("su", "runuser"):            # su -c 'cmd', su - user -c 'cmd', su --command='cmd'
        for i, a in enumerate(args):
            if a.startswith(("--command=", "--session-command=")):
                codes.append(a.split("=", 1)[1])
            elif (a in ("--command", "--session-command") or (a.startswith("-") and not a.startswith("--")
                                                              and a.endswith("c"))) and i + 1 < len(args):
                codes.append(args[i + 1])
    if cmd == "find":                       # find ... -exec <command> ;   the command runs once per match
        i = 0
        while i < len(args):
            if args[i] in ("-exec", "-execdir", "-ok", "-okdir"):
                end = next((j for j in range(i + 1, len(args)) if args[j] in (";", "+")), len(args))
                if end > i + 1:
                    codes.append(" ".join(shlex.quote(w) for w in args[i + 1:end]))
                i = end
            i += 1
    return codes


def reads_stdin_program(words):
    """True when a shell or an interpreter runs a program read from stdin (bare, '-', or shell -s), so text
    piped or redirected to it is code. A script file (`python3 s.py`), a module (`python3 -m m`) or inline
    code (-c/-e/--eval) is not: there that text is data the program reads, not commands."""
    if not words:
        return False
    cmd, args = os.path.basename(words[0]), words[1:]
    if not (is_interpreter(cmd) or cmd == "perl") or inline_code(words):
        return False
    if any(a in ("--eval", "--print") or a.startswith(("--eval=", "--print=")) for a in args):
        return False
    if cmd in SHELLS:
        return shell_arguments(args)[1]      # `bash`, `bash -s`, `bash -o pipefail`: no script operand
    if any(a == "-m" or (a.startswith("-m") and not a.startswith("--")) for a in args):
        return False
    return not [a for a in args if not a.startswith("-")]   # no script-file argument: the program is stdin


def pipe_points(command):
    """Offsets of every `|` that joins two pipeline stages (not `||`, not the `>|` of a redirection) outside quotes."""
    points, quote, i, n = [], None, 0, len(command)
    while i < n:
        c = command[i]
        if quote == "'":
            if c == "'":
                quote = None
        elif c == "\\":
            i += 1
        elif c in "'\"":
            if quote is None:
                quote = c
            elif quote == c:
                quote = None
        elif c == "|" and quote is None:
            if command.startswith("||", i):
                i += 1
            elif not redirection_part(command, i):
                points.append(i)
        i += 1
    return points


def pipeline_start(command, point):
    """Where the pipeline that reaches the `|` at point begins: after the last `;`, `&&`, `||`, `&` or newline of
    its own nesting level. A group `( ... )` that is piped as a whole belongs to it."""
    starts, quote, i = [0], None, 0
    while i < point:
        c = command[i]
        if quote:
            if c == "\\" and quote == '"':
                i += 1
            elif c == quote:
                quote = None
        elif c == "\\":
            i += 1
        elif c in "'\"":
            quote = c
        elif c == "(":
            starts.append(i + 1)
        elif c == ")":
            if len(starts) > 1:
                starts.pop()
        elif command.startswith(("&&", "||"), i):
            i += 1
            starts[-1] = i + 1
        elif c in ";\n" or (c == "&" and command[i - 1:i] not in ("<", ">", "|") and command[i + 1:i + 2] != ">"):
            starts[-1] = i + 1
        i += 1
    return starts[-1]


def piped_shell_code(command):
    """Commands that stand for text piped into a shell or an interpreter that reads its program from stdin
    (`echo 'git push --force' | bash`, `printf '...' | python3`). Everything in the SAME pipeline left of that
    pipe can end up in the program: every quoted fragment, the words of every echo/printf and every here-string
    or heredoc - also when a filter sits in between (`cat <<EOF | bash`), the stage is inside
    `if ...; then ...; fi`, the pipe is `|&` or the reading stage starts on the next line. An earlier command
    (`grep 'rm -rf gauntlet' notes.md; curl ... | bash`) feeds nothing into it."""
    codes = []
    for point in pipe_points(command):
        after = command[point + 1:].lstrip("&").lstrip()     # |& pipes stderr as well
        reader = words_of(re.split(r"\||;|\n|\)|(?<![<>])&(?!>)", after, maxsplit=1)[0])   # the command that reads the pipe
        if not reads_stdin_program(reader):
            continue
        left = command[pipeline_start(command, point):point]
        texts = [m.group(0)[1:-1] for m in QUOTED.finditer(left)]
        for segment in split_shell(left):
            if segment in ("(", ")"):
                continue
            texts.extend(text for text, _ in here_strings(segment)[1])
            words = words_of(segment)
            if not words:
                continue
            lcmd = os.path.basename(words[0])
            if lcmd == "echo":
                rest = words[1:]
                while rest and rest[0] in ("-n", "-e", "-E", "-ne", "-en", "-nE"):
                    rest = rest[1:]
                if rest:
                    texts.append(" ".join(rest))
            elif lcmd == "printf":
                if len(words) > 1:
                    texts.append(" ".join(words[1:]))   # the format string may itself be the payload
                if len(words) > 2:
                    texts.append(" ".join(words[2:]))   # ...or the args printed after the format
        codes.extend(as_program(reader, text) for text in texts)
    return codes


ANSI_C = {"a": "\x07", "b": "\x08", "e": "\x1b", "E": "\x1b", "f": "\x0c", "n": "\n", "r": "\r", "t": "\t",
          "v": "\x0b", "\\": "\\", "'": "'", '"': '"', "?": "?"}


def ansi_c_text(body):
    """The text bash makes of $'body': its backslash escapes decoded (octal, \\xHH, \\uHHHH, \\cX ...); a NUL ends it.
    `bash -c $'git push --force'` runs `git push --force` - the rules must see those words, not `$git`."""
    out, i, n = [], 0, len(body)
    while i < n:
        c = body[i]
        if c != "\\" or i + 1 >= n:
            out.append(c)
            i += 1
            continue
        e = body[i + 1]
        digits = None
        if e in ANSI_C:
            out.append(ANSI_C[e])
            i += 2
        elif e in "01234567":
            digits = re.match(r"[0-7]{1,3}", body[i + 1:]).group(0)
            out.append(chr(int(digits, 8) & 0xFF))
            i += 1 + len(digits)
        elif e in "xuU" and re.match(r"[0-9a-fA-F]", body[i + 2:i + 3]):
            digits = re.match("[0-9a-fA-F]{1,%d}" % {"x": 2, "u": 4, "U": 8}[e], body[i + 2:]).group(0)
            code = int(digits, 16)
            if code > 0x10FFFF or 0xD800 <= code <= 0xDFFF:     # no character: left as written
                out.append(body[i:i + 2 + len(digits)])
            else:
                out.append(chr(code))
            i += 2 + len(digits)
        elif e == "c" and i + 2 < n:
            out.append(chr(ord(body[i + 2]) & 0x1F))
            i += 3
        else:
            out.append(c + e)
            i += 2
    return "".join(out).split("\x00", 1)[0]


def ansi_c_end(command, i):
    """Index just past the quote that closes a $'...' string whose text starts at i; an escaped quote inside
    does not end it."""
    n = len(command)
    while i < n and command[i] != "'":
        i += 2 if command[i] == "\\" else 1
    return i + 1


def backtick_end(command, i):
    """Index just past the backtick that closes a `...` substitution whose body starts at i; an escaped
    backtick inside belongs to a nested substitution."""
    n = len(command)
    while i < n and command[i] != "`":
        i += 2 if command[i] == "\\" else 1
    return i + 1


def substitution_end(command, i):
    """Index just past the `)` that closes a `$(` whose body starts at i (len + 1 when it never closes).
    Quoting is per level: every nested $( ... ) and `...` opens a fresh context, so a quoted `)` inside it does
    not end this body. (The `)` of a case pattern has its own `(` after shell_code.)"""
    n, quote, depth = len(command), None, 0
    while i < n:
        c = command[i]
        if quote == "'":
            if c == "'":
                quote = None
        elif c == "\\":
            i += 1
        elif quote is None and command.startswith("$'", i):
            i = ansi_c_end(command, i + 2) - 1
        elif command.startswith("$(", i):
            i = substitution_end(command, i + 2) - 1
        elif c == "`":
            i = backtick_end(command, i + 1) - 1
        elif c == '"':
            quote = None if quote == '"' else '"'
        elif quote is None:
            if c == "'":
                quote = "'"
            elif c == "(":
                depth += 1
            elif c == ")":
                if depth == 0:
                    return i + 1
                depth -= 1
        i += 1
    return n + 1


def command_substitutions(command):
    """Bodies of $(...) and `...` that the shell actually executes: unquoted and inside double quotes run
    substitutions, inside single quotes they are literal text. Returned bodies are re-checked by the caller,
    which recurses into nested substitutions on its own."""
    bodies = []
    i, n, quote = 0, len(command), None
    while i < n:
        c = command[i]
        if quote == "'":
            if c == "'":
                quote = None
        elif c == "\\":
            i += 1                              # an escaped character, also inside double quotes
        elif quote is None and command.startswith("$'", i):
            i = ansi_c_end(command, i + 2) - 1
        elif command.startswith("$(", i):
            end = substitution_end(command, i + 2)
            bodies.append(command[i + 2:end - 1])
            i = end - 1
        elif c == "`":
            end = backtick_end(command, i + 1)
            # the shell removes one level of backslashes before it runs the body
            bodies.append(re.sub(r"\\([`$\\])", r"\1", command[i + 1:end - 1]))
            i = end - 1
        elif c == '"':
            quote = None if quote == '"' else '"'
        elif c == "'" and quote is None:
            quote = "'"
        i += 1
    return bodies


def gh_human_only(args):
    rest = list(args)
    while rest and rest[0].startswith("-"):          # global options: -R/--repo <owner/repo>, --hostname <host>
        opt = rest.pop(0)
        if opt in ("-R", "--repo", "--hostname") and rest:
            rest.pop(0)
    if rest[:2] == ["pr", "merge"] or rest[:1] == ["merge"]:
        return "Merging the pull request is the human's decision. Push the branch, open the PR and report."
    if rest[:1] == ["repo"] and rest[1:2] and rest[1] in ("delete", "edit", "rename", "archive"):
        return "Changing the repository itself is reserved for the human."
    if rest[:2] == ["workflow", "disable"]:
        return CI_SWITCH
    if rest[:1] == ["api"]:
        # judged by what the call does - its endpoint and method, for GraphQL the mutation it names - never by a
        # word in a field value or a jq filter: a reply in a review thread may say "merge" and "/protection"
        endpoint, method, fields = gh_api_call(rest[1:])
        path = endpoint.split("?", 1)[0].rstrip("/").lower()
        if path.endswith("graphql"):
            query = " ".join(f.split("=", 1)[1] for f in fields if f.startswith("query="))
            if GH_MERGE_MUTATION.search(query):
                return "Merging through the GitHub API is reserved for the human."
            if GH_PROTECTION_MUTATION.search(query):
                return CI_SWITCH
        elif method != "GET":
            if re.search(r"/pulls/[^/]+/merge$|/merges$|/merge-upstream$", path):
                return "Merging through the GitHub API is reserved for the human."
            # reading the protection is fine; changing or deleting it, a ruleset, the Actions permissions or
            # disabling a workflow takes the CI gate away
            if re.search(r"/protection(/|$)|/rulesets(/|$)|/actions/permissions(/|$)|/actions/workflows/[^/]+/disable$", path):
                return CI_SWITCH
    return None


def gh_api_call(args):
    """(endpoint, method, fields) of a `gh api` call. The endpoint is the first operand - the value of an option
    is not one. The method is -X/--method when given, otherwise POST as soon as a field or a request body is
    sent and GET for a plain read (gh's own default). fields: the `key=value` of every -f / -F."""
    endpoint, method, fields, sends, i = None, "", [], False, 0
    while i < len(args):
        a = args[i]
        option, value = None, ""
        if a.startswith("--"):
            name, attached, value = a.partition("=")
            if name in GH_API_VALUE_OPTIONS:
                option = name
                if not attached:
                    i += 1
                    value = args[i] if i < len(args) else ""
        elif a.startswith("-") and a[:2] in GH_API_VALUE_OPTIONS:
            option = a[:2]
            if len(a) > 2:
                value = a[2:].lstrip("=")
            else:
                i += 1
                value = args[i] if i < len(args) else ""
        elif not a.startswith("-") and endpoint is None:
            endpoint = a
        if option in ("-X", "--method"):
            method = value
        elif option in ("-f", "-F", "--field", "--raw-field"):
            fields.append(value)
            sends = True
        elif option == "--input":
            sends = True
        i += 1
    return endpoint or "", (method or ("POST" if sends else "GET")).upper(), fields


def push_branch_dir(args, cwd):
    """Directory whose HEAD a push acts on: honour -C and --git-dir, as git_subcommand/path_tokens do.
    A worktree on the base branch is pushed even when the session's own project sits on a feature branch."""
    base, git_dir = cwd, None
    sub = git_subcommand(args)
    region = args[:args.index(sub)] if sub in args else args
    i = 0
    while i < len(region):
        a = region[i]
        if a == "-C" and i + 1 < len(region):
            t = region[i + 1]
            base = os.path.realpath(t if os.path.isabs(t) else os.path.join(base, t))
            i += 2
            continue
        if a.startswith("-C") and not a.startswith("--") and len(a) > 2:
            t = a[2:]
            base = os.path.realpath(t if os.path.isabs(t) else os.path.join(base, t))
        elif a == "--git-dir" and i + 1 < len(region):
            git_dir = region[i + 1]
            i += 2
            continue
        elif a.startswith("--git-dir="):
            git_dir = a.split("=", 1)[1]
        i += 1
    return base, git_dir


def push_current_branch(args, cwd):
    base, git_dir = push_branch_dir(args, cwd)
    if git_dir:
        return git(base, "--git-dir", git_dir, "symbolic-ref", "--short", "HEAD")
    return git(base, "symbolic-ref", "--short", "HEAD")


def push_branches(args, cwd, project):
    """The branches a push of HEAD may publish: the branch of the directory it runs in - of every candidate
    after a cd the hook could not follow."""
    return {push_current_branch(args, c) for c in candidates(cwd, project)}


def long_opt(arg, name, min_len):
    """Git accepts every unambiguous prefix of a long option: `git commit --amen` amends, `git push --mirro`
    mirrors. True when arg - up to an optional `=value` - is such a prefix of name, at least min_len characters
    long. min_len is the shortest prefix some git version resolves to this option; anything shorter is rejected
    by git as ambiguous, and a prefix that belongs to another option (`--fol` is --follow-tags, `--form` is
    --format) is no prefix of name at all."""
    opt = arg.split("=", 1)[0]
    return len(opt) >= min_len and opt.startswith("--") and name.startswith(opt)


def commit_options(rest):
    """The option tokens of a `git commit` command line. The value of an option that takes one (-m "--amend",
    --message "...", the argument after a cluster such as -am) and everything after `--` are data, not options."""
    options, skip = [], False
    for a in rest:
        if skip:
            skip = False
        elif a == "--":
            break
        elif a in GIT_VALUE_OPTIONS:
            skip = True
        elif a.startswith("--"):
            options.append(a)
        elif a.startswith("-"):
            for i, flag in enumerate(a[1:]):      # short-option cluster such as -an or -am "msg"
                if flag in "Su":                  # -S[<key-id>] / -u[<mode>]: the rest of the cluster is their value
                    break
                if flag in "mFCct":               # takes a value: attached ("-mfix") or the next argument
                    skip = i == len(a) - 2
                    break
    return options


def recreates_branch(sub, rest):
    """`git checkout -B` / `git switch -C|--force-create`: also abbreviated (`--force-c`), with the name attached
    (`-Bname`) or inside a short cluster (`-qB`, `-fC`). The lower-case -b / -c create a NEW branch and their
    attached value is a name (`-bBugfix`, `-cCleanup`), not more flags; `--force` alone only discards changes."""
    force, create = ("B", "b") if sub == "checkout" else ("C", "c")
    for a in rest:
        if a == "--":
            break
        if a in ("-B", "-C") or long_opt(a, "--force-create", 8):
            return True
        if a.startswith("-") and not a.startswith("--"):
            for flag in a[1:]:
                if flag == force:
                    return True
                if flag == create:
                    break                         # the rest of the cluster is the new branch's name
    return False


def guarded_config_key(key):
    """Config keys only the human sets: core.hooksPath switches the project's git hooks off, an alias or an
    included config file hides any human-only command behind another name. Git compares section and variable
    names case-insensitively."""
    k = key.lower()
    return k == "core.hookspath" or k.startswith("alias.") or k == "include.path" or k.startswith("includeif.")


def config_reason(key):
    k = key.lower()
    if k == "core.hookspath":
        return "Setting core.hooksPath switches the project's git hooks off. Reserved for the human."
    if k.startswith("alias."):
        return "A git alias can hide a human-only command behind another name. Reserved for the human."
    return "Including another git config file can bring in aliases or core.hooksPath. Reserved for the human."


def config_override(args):
    """A guarded key set for this one command: the value of a global `-c name=value` or `--config-env
    name=ENVVAR` before the subcommand - whatever the subcommand is, and not a commit message that happens
    to mention core.hooksPath."""
    index = git_subcommand_index(args)
    region = args[:index] if index >= 0 else args
    i = 0
    while i < len(region):
        a = region[i]
        value = None
        if a in ("-c", "--config-env"):
            value = region[i + 1] if i + 1 < len(region) else ""
            i += 1
        elif a.startswith("--config-env="):
            value = a.split("=", 1)[1]
        elif a.startswith("-c") and not a.startswith("--") and len(a) > 2:
            value = a[2:]
        if value is not None and guarded_config_key(value.split("=", 1)[0]):
            return value.split("=", 1)[0]
        i += 1
    return None


def config_write(rest):
    """The guarded key a `git config` command writes, or None: `git config <key> <value>`, also with --add /
    --replace-all, a file option (--global, --file f) or the newer `git config set`; and `--rename-section
    <old> alias|core|include`, which turns an ordinary section into a guarded one. Reads (a name alone, --get*,
    --list, `get`) and --unset* / --remove-section stay allowed: they neither switch a hook off nor create
    an alias."""
    positional, rename, skip, literal = [], False, False, False
    for a in rest:
        if skip:
            skip = False
        elif literal or not a.startswith("-") or a == "-":
            positional.append(a)
        elif a == "--":
            literal = True
        elif a == "-l" or long_opt(a, "--list", 4) or a.startswith(("--get", "--unset")) \
                or long_opt(a, "--remove-section", 5):
            return None
        elif long_opt(a, "--rename-section", 5):
            rename = True
        elif "=" not in a and (a in ("-f", "-t") or any(long_opt(a, name, m) for name, m in GIT_CONFIG_VALUE_OPTIONS)):
            skip = True                           # the next argument is this option's value, not a key
    action = positional[0] if positional else ""
    if action in ("get", "list", "unset", "remove-section", "edit"):
        return None                               # subcommands of newer git; an older git rejects them as keys
    if rename or action == "rename-section":
        names = positional[1:] if action == "rename-section" else positional
        target = names[1].lower() if len(names) > 1 else ""
        if target in ("alias", "core", "include") or target.startswith("includeif"):
            return {"alias": "alias.*", "core": "core.hooksPath"}.get(target, "include.path")
        return None
    return next((p for p in positional[:-1] if guarded_config_key(p)), None)   # a key followed by its value


def git_human_only(args, ctx, cwd):
    index = git_subcommand_index(args)
    sub = args[index] if index >= 0 else ""
    rest = args[index + 1:] if index >= 0 else []
    key = config_override(args) or (config_write(rest) if sub == "config" else None)
    if key:
        return config_reason(key)
    if skips_git_hooks(args):
        return "Skipping git hooks (--no-verify) is reserved for the human."
    if sub in HISTORY_REWRITING:
        return (f"git {sub} rewrites or undoes history, which may drop the approved specification commit. "
                f"Reserved for the human (fix forward with a new commit instead).")
    if sub == "commit" and any(long_opt(a, "--amend", 4) for a in commit_options(rest)):
        return "git commit --amend rewrites history (the approved spec commit must stay as it is). Make a new commit."
    if sub == "push":
        if any(a.startswith("--force") or long_opt(a, "--force", 5)
               or (a.startswith("-") and not a.startswith("--") and "f" in a[1:]) for a in rest):
            return "Force-pushing is reserved for the human."
        # --branches is git's own alias of --all (2.43); every prefix from --m / --a / --b on means one of them
        if any(long_opt(a, "--mirror", 3) or long_opt(a, "--all", 3) or long_opt(a, "--branches", 3) for a in rest):
            return ("git push --mirror/--all rewrites every ref including the base branch. "
                    "Reserved for the human. Push your own branch only.")
        refspecs = [a for a in rest if not a.startswith("-")][1:]   # after the remote name
        if any(a.startswith("+") or a == ctx.base_branch or a.endswith(":" + ctx.base_branch)
               or a.endswith("/" + ctx.base_branch) for a in refspecs):
            return f"Pushing to {ctx.base_branch} is the human's decision (merge the PR). Push your own branch only."
        if any(a in ("HEAD", "@") for a in refspecs):   # bare HEAD/@ pushes the current branch; @ is git's HEAD synonym
            if ctx.base_branch in push_branches(args, cwd, ctx.project):
                return (f"Pushing HEAD while on {ctx.base_branch} pushes the base branch. "
                        "Reserved for the human. Push your own branch only.")
        if not refspecs:
            if ctx.base_branch in push_branches(args, cwd, ctx.project):
                return (f"Pushing without a refspec while on {ctx.base_branch} pushes the base branch "
                        "(push.default=simple). Reserved for the human. Push your own branch only.")
    if sub == "reset":
        before_dashes = rest[:rest.index("--")] if "--" in rest else rest
        positional = [a for a in before_dashes if not a.startswith("-")]
        revision = positional[0] if positional else None
        # `git reset`, `git reset HEAD file`, `git reset file` unstage; anything else moves the branch
        if revision not in (None, "HEAD") and not os.path.exists(os.path.join(cwd, revision)):
            return "git reset to another revision drops commits (possibly the approved spec). Reserved for the human."
    if sub in ("checkout", "switch") and recreates_branch(sub, rest):
        return "Re-creating the branch at another revision drops its commits (the approved spec). Reserved for the human."
    if sub == "branch" and any(a in ("-f", "-D", "-M", "-d", "-m", "-c", "-C")
                               or long_opt(a, "--force", 3) or long_opt(a, "--delete", 3)
                               or long_opt(a, "--move", 4) or long_opt(a, "--copy", 5)
                               or (a.startswith("-") and not a.startswith("--") and any(ch in "fDMdmcC" for ch in a[1:]))
                               for a in branch_options(rest)):
        return "Moving, forcing or deleting branches is reserved for the human."
    return None


# `git switch` and `git checkout` without a pathspec write exactly the files that differ between HEAD and the commit
# they move to: they are judged by that difference (switch_judgment), not by the text of the command. Their options
# that change nothing of it; any other option - a pathspec after --, -p, --ours, --pathspec-from-file, an
# abbreviation - leaves the command to the text match.
SWITCH_FLAGS = {"-q", "--quiet", "--progress", "--no-progress", "-f", "--force", "--discard-changes", "-m", "--merge",
                "-d", "--detach", "--guess", "--no-guess", "--ignore-other-worktrees", "--overwrite-ignore",
                "--no-overwrite-ignore", "--recurse-submodules", "--no-recurse-submodules", "--no-track", "-l"}
SWITCH_GLOBALS = {"--no-pager", "-P", "--no-optional-locks"}       # git options that leave the repository as it is
# the environment that moves git to another repository, work tree or index: the switch is then left to the text match
GIT_LOCATION = re.compile(r"\bGIT_(?:DIR|WORK_TREE|INDEX_FILE|COMMON_DIR|OBJECT_DIRECTORY|ALTERNATE_OBJECT_DIRECTORIES|"
                          r"NAMESPACE|CONFIG\w*|CEILING_DIRECTORIES|DISCOVERY_ACROSS_FILESYSTEM)\b")


def switch_form(sub, rest):
    """What a `git switch` / `git checkout` without a pathspec moves the work tree to, as (kind, revision, force,
    guess): ("ref", rev) - an existing branch, tag or commit, `-`, with or without --detach, and HEAD for a bare
    `git checkout -f`; ("new", start) - a new branch (-c, -b, --orphan of checkout, -t) at its start point or HEAD;
    ("orphan", None) - `git switch --orphan`, which removes every tracked file. force: it also discards local
    changes (-f, --discard-changes); guess: it may take a branch of that name from the one remote that has it. None
    for any other form."""
    create = ("-b", "--orphan") if sub == "checkout" else ("-c", "--create", "--orphan")
    attached = "-b" if sub == "checkout" else "-c"
    kind, operands, force, guess, i = "ref", [], False, True, 0
    while i < len(rest):
        a = rest[i]
        name = a.split("=", 1)[0]
        if a == "--":
            if sub == "switch" or rest[i + 1:]:
                return None                     # a pathspec
            break
        if name in create:
            if "=" not in a:
                i += 1                          # the name of the new branch
                if i >= len(rest):
                    return None
            kind = "orphan" if name == "--orphan" and sub == "switch" else "new"
        elif a.startswith(attached) and len(a) > 2:
            kind = "new"                        # -bname, -cname
        elif name in ("-t", "--track"):
            kind = "new" if kind == "ref" else kind     # a branch named after the remote one it tracks
        elif a in SWITCH_FLAGS or name == "--conflict" or re.fullmatch(r"-[qfm]+", a):
            force = force or a in ("--force", "--discard-changes") or (not a.startswith("--") and "f" in a)
            guess = guess and a != "--no-guess"
        elif a.startswith("-") and a != "-":
            return None
        else:
            operands.append(a)
        i += 1
    if kind == "orphan":
        return None if operands else ("orphan", None, force, guess)
    if kind == "new":
        return ("new", operands[0] if operands else "HEAD", force, guess) if len(operands) <= 1 else None
    if not operands and force and sub == "checkout":
        return ("ref", "HEAD", force, guess)    # `git checkout -f` throws every local change away
    return ("ref", operands[0], force, guess) if len(operands) == 1 else None


def git_paths(top, *args):
    """The NUL-separated paths a git command prints in the work tree `top`, or None when it fails. It takes no
    optional lock: comparing the work tree does not rewrite the index for the command that follows."""
    try:
        r = subprocess.run(["git", "--no-optional-locks", "-C", top, "--literal-pathspecs", *args],
                           stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=30)
    except (OSError, subprocess.TimeoutExpired):
        return None
    return [p for p in r.stdout.decode("utf-8", "surrogateescape").split("\0") if p] if r.returncode == 0 else None


def switch_commit(top, rev, guess, kind):
    """The commit the switch moves to, or None when the guard cannot find it: the revision itself (`-` is the branch
    checked out before), else - for a branch that is not here yet - the one remote branch of that name git takes."""
    sha = git(top, "rev-parse", "--verify", "--quiet", ("@{-1}" if rev == "-" else rev) + "^{commit}")
    if sha or not guess or kind != "ref" or rev.startswith("-"):
        return sha or None
    remotes = git(top, "remote").split()
    refs = [r for r in git(top, "for-each-ref", "--format=%(refname)", "refs/remotes/").splitlines()
            if any(r == f"refs/remotes/{remote}/{rev}" for remote in remotes)]
    return (git(top, "rev-parse", "--verify", "--quiet", refs[0] + "^{commit}") or None) if len(refs) == 1 else None


ACTIVE_HOOKS = (".claude/", "gauntlet/hooks/")


def switch_judgment(agent, words, command, cwd, ctx):
    """(True, refusal or None) for a git switch the guard judges by the files it changes: those that differ between
    HEAD and the commit it moves to (every tracked file for `switch --orphan`), and with -f the locally changed ones
    it discards. A human-owned or protected specification file among them is refused unless the commit holds the base
    branch's version of it - switching back to the base branch's kit is no change of the kit, except that a frozen
    specifier may not switch the spec back and active hooks (.claude/, gauntlet/hooks/) added or edited on this branch
    may not be wiped or rolled back - and a local change is refused whenever it would be discarded; so is a file of
    the user's agent configuration, and for the specifier and the hardener a file outside their scope. The name of the
    branch does not matter. (False, None) for a command that is no such switch or one the guard cannot resolve - a ref
    it does not find, a variable, git moved by -c, --git-dir or GIT_DIR, a directory it did not follow: the caller
    judges it by its text."""
    if not words or os.path.basename(words[0]) != "git" or not known(cwd) \
            or GIT_LOCATION.search(QUOTED.sub("''", command)):
        return False, None
    args = words[1:]
    index = git_subcommand_index(args)
    if index < 0 or args[index] not in ("switch", "checkout"):
        return False, None
    # a cd into a directory that is not there fails, and git runs where the command was before it
    base = next((d for d in (str(cwd),) + tuple(reversed(getattr(cwd, "seen", ()))) if os.path.isdir(d)), ctx.project)
    k = 0
    while k < index:                            # git -C a -C b: b is relative to a
        if args[k] == "-C" and k + 1 < index:
            base = os.path.join(base, expand(args[k + 1], cwd, ctx.project)) if args[k + 1] else base
            k += 2
        elif args[k] in SWITCH_GLOBALS:
            k += 1
        else:
            return False, None
    form = switch_form(args[index], args[index + 1:])
    if form is None or any(UNEXPANDED.search(a) for a in args):
        return False, None
    kind, rev, force, guess = form
    top = git(base, "rev-parse", "--show-toplevel") if os.path.isdir(base) else ""
    if not top:
        return False, None
    if kind == "ref" and args[index] == "checkout" and os.path.exists(os.path.join(base, rev)):
        guess = False                           # checkout takes a name that is a path for that path, no remote branch
    commit = None if kind == "orphan" else switch_commit(top, rev, guess, kind)
    if kind != "orphan" and commit is None:
        return False, None
    changed = (git_paths(top, "ls-tree", "-r", "-z", "--name-only", "HEAD") if kind == "orphan" else
               git_paths(top, "diff", "--name-only", "-z", "--no-renames", "--no-ext-diff", "HEAD", commit, "--"))
    local = git_paths(top, "diff", "--name-only", "-z", "--no-renames", "--no-ext-diff", "HEAD", "--") if force else []
    if changed is None or local is None:
        return False, None
    scope = ctx.role_scope(agent)
    for p in changed + local:                   # a repository in the home directory holds the user's configuration
        reason = user_config_violation(os.path.join(top, p))
        if reason:
            return True, reason
        rel = ctx.relative(os.path.join(top, p))
        if scope and rel is not None and not is_under(rel, scope):
            return True, f"The {agent} may only change files under {', '.join(scope)} (this switch changes {rel})."
    protected = tuple(ctx.protected(agent))
    rels = {p: ctx.relative(os.path.join(top, p)) or ".." for p in set(changed + local)}
    guarded = {p for p, rel in rels.items() if is_under(rel, protected)}
    discarded, moved = [p for p in local if p in guarded], [p for p in changed if p in guarded]
    allowable = set()
    if moved and commit is not None:            # the base branch's version is the human's
        pinned = set()
        hook_moves = {p for p in moved if is_under(rels[p], ACTIVE_HOOKS)}
        if hook_moves:
            mb = git(top, "merge-base", ctx.base_ref, "HEAD")
            branch_diff = (git_paths(top, "diff", "--name-only", "-z", "--no-renames", "--no-ext-diff", mb, "HEAD",
                                     "--") if mb else None)
            pinned = hook_moves if branch_diff is None else (hook_moves & set(branch_diff))
        allowable = {p for p in moved if p not in pinned
                     and not (agent == "specifier" and not is_under(rels[p], HUMAN_OWNED))}
        if allowable:
            differs = git_paths(top, "diff", "--name-only", "-z", "--no-renames", "--no-ext-diff", ctx.base_ref,
                                commit, "--", *sorted(allowable))
            if differs is not None:
                moved = [p for p in moved if p not in allowable or p in differs]
    hits = discarded + moved
    if not hits:
        return True, None
    hit = hits[0]
    if not is_under(rels[hit], HUMAN_OWNED):
        action = "would discard the local change of" if hit in discarded else "would change"
        tail = frozen_reason(rels[hit]) if ctx.frozen else "Only the specifier subagent writes the Gherkin specification."
        return True, f"This switch {action} {hit}: {tail}"
    if hit in discarded:
        what = f"would discard the local change of {hit}, a human-owned file"
    elif hit not in allowable and commit is not None:
        what = f"would change or remove {hit}, an active hook or settings file changed on this branch"
    else:
        what = (f"would change {hit}, a human-owned file: the revision it moves to holds a version of it that neither "
                f"HEAD nor the base branch ({ctx.base_ref}) has")
    return True, (f"This switch {what}. Moving the kit or the agents' instructions to another version is reserved for "
                  "the human. Read such a file with `git show <revision>:<path>`, or check the revision out outside "
                  "the project (`git worktree add --detach /tmp/<dir> <revision>`).")


def branch_options(rest):
    """The option words of `git branch` without what is a VALUE: in `git branch --sort -committerdate` the second
    word is a sort key and in `git branch -uorigin/claude/x` the rest of the word is an upstream - neither is a
    cluster of -c -m -d flags."""
    kept, skip = [], False
    for a in rest:
        if skip:
            skip = False
            continue
        if a.startswith("-u") and len(a) > 2:
            a = "-u"
        kept.append(a)
        skip = a == "-u" or ("=" not in a and any(long_opt(a, name, m) for name, m in BRANCH_VALUE_OPTIONS))
    return kept


def skips_git_hooks(args):
    """--no-verify (any prefix git resolves) as an OPTION of a command that runs hooks, and -n of git commit. The
    same letters as a value - `git log --grep "--no-verify"`, a commit message - skip nothing."""
    index = git_subcommand_index(args)
    sub = args[index] if index >= 0 else ""
    rest = args[index + 1:] if index >= 0 else []
    if sub != "commit":
        options = rest[:rest.index("--")] if "--" in rest else rest
        return sub in HOOK_RUNNING and any(a.startswith("--no-v") and "--no-verify".startswith(a) for a in options)
    if any(a.startswith("--no-v") and "--no-verify".startswith(a) for a in commit_options(rest)):
        return True
    skip = False
    for a in rest:
        if skip:
            skip = False
        elif a == "--":
            break
        elif a in GIT_VALUE_OPTIONS:
            skip = True
        elif a.startswith("-") and not a.startswith("--"):
            for i, flag in enumerate(a[1:]):      # short-option cluster such as -an or -am "msg"
                if flag in "Su":                  # -S[<key-id>] / -u[<mode>]: the rest is their value (-uno, -Sname)
                    break
                if flag == "n":
                    return True
                if flag in "mFCct":               # takes a value: attached ("-mfix") or the next argument
                    skip = i == len(a) - 2
                    break
    return False


SEPARATOR = re.compile(r"\|\||&&|[;|&`\n]|\$\(|[()]")


def unescaped(command, i):
    """True when no backslash escapes the character at i: an even number of them stands right before it."""
    k = i
    while k > 0 and command[k - 1] == "\\":
        k -= 1
    return (i - k) % 2 == 0


def redirection_part(command, i):
    """True when the | or & at i belongs to a redirection - >|, 2>&1, <&3, &>file - and ends no command. The < or >
    before it must be an operator: after `echo x \\>` the | is a pipe and the & sends the echo to the background."""
    if command[i] == "&" and command[i + 1:i + 2] == ">":
        return True
    before = command[i - 1:i]
    return (before == ">" or before == "<" and command[i] == "&") and unescaped(command, i - 1)


def separators(command):
    """What ends a simple command: ;, &&, ||, a newline, a pipe, & - and $( ), ( ) and backticks."""
    return [m for m in SEPARATOR.finditer(command) if m.group(0) not in "|&" or not redirection_part(command, m.start())]


def segments(command):
    out, last = [], 0
    for m in separators(command):
        out.append(command[last:m.start()])
        last = m.end()
    out.append(command[last:])
    return [s for s in out if s.strip()]


def split_shell(command):
    """Like segments, but separators inside quotes do not split: `bash -c 'echo ok; git push --force'`
    stays one segment so the inline code reaches inline_code_strings intact (the ';' is code, not a separator).
    Subshell boundaries are emitted as standalone '(' / ')' markers (a '$(' and a backtick open a scope too) so
    the caller can keep a cd inside ( ... ) from leaking to the parent shell."""
    parts, buf = [], []
    quote, esc, i, n, backtick = None, False, 0, len(command), False
    while i < n:
        c = command[i]
        if esc:
            buf.append(c)
            esc = False
        elif c == "\\" and quote != "'":
            buf.append(c)
            esc = True
        elif quote:
            buf.append(c)
            if c == quote:
                quote = None
        elif command.startswith("$'", i):       # ANSI-C string: an escaped quote inside does not close it
            end = ansi_c_end(command, i + 2)
            buf.append(command[i:end])
            i = end - 1
        elif c in "'\"":
            quote = c
            buf.append(c)
        elif command.startswith(("||", "&&"), i):
            parts.append("".join(buf))
            buf = []
            i += 1
        elif command.startswith("$(", i):
            parts.append("".join(buf))
            buf = []
            parts.append("(")
            i += 1
        elif c in "()":
            parts.append("".join(buf))
            buf = []
            parts.append(c)
        elif c == "`":                           # a backtick substitution is a subshell scope too
            parts.append("".join(buf))
            buf = []
            parts.append(")" if backtick else "(")
            backtick = not backtick
        elif c in ";\n" or (c in "|&" and not redirection_part(command, i)):
            parts.append("".join(buf))             # not the | of >|file, the & of 2>&1 or &>file
            buf = []
        else:
            buf.append(c)
        i += 1
    parts.append("".join(buf))
    return [s for s in parts if s.strip()]


def segments_scoped(command):
    """segments() for quote-neutralised text, but subshell parens are emitted as '(' / ')' markers so a cd
    inside ( ... ), $( ... ) or backticks is scoped to the subshell and cannot leak to the parent shell's commands."""
    out, last, backtick = [], 0, False
    for m in separators(command):
        out.append(command[last:m.start()])
        sep = m.group(0)
        if sep == "`":
            out.append(")" if backtick else "(")
            backtick = not backtick
        elif sep in ("(", "$("):
            out.append("(")
        elif sep == ")":
            out.append(")")
        last = m.end()
    out.append(command[last:])
    return [s for s in out if s.strip()]


def words_of(segment):
    segment = here_strings(segment)[0]      # `<<< text` is what the command reads, not an argument of it
    try:
        words = shlex.split(segment, posix=True)
    except ValueError:
        words = segment.split()
    # a wrapper is the same program whatever path it is called by: /usr/bin/env, /usr/bin/sudo
    while words and (re.match(r"^\w+=", words[0]) or os.path.basename(words[0]) in WRAPPERS
                     or words[0] in SHELL_KEYWORDS):
        wrapper = words.pop(0)
        if wrapper not in SHELL_KEYWORDS:
            wrapper = os.path.basename(wrapper)
        if wrapper == "env":
            split_value = None
            while words and words[0].startswith("-"):
                opt = words.pop(0)
                name = opt.split("=", 1)[0]
                if opt.startswith("--"):
                    if long_opt(opt, "--split-string", 3):
                        # the value IS the command: env splits and executes it, so surface it
                        split_value = opt.split("=", 1)[1] if "=" in opt else (words.pop(0) if words else None)
                        break
                    if "=" not in opt and name in WRAPPER_VALUE_OPTS["env"] and words:
                        words.pop(0)
                    continue
                for i, flag in enumerate(opt[1:], 1):     # a short cluster: -iS 'cmd', -S'cmd', -uNAME, -C dir
                    if flag == "S":
                        split_value = opt[i + 1:] or (words.pop(0) if words else None)
                        break
                    if flag in "uC":                      # takes a value: the rest of the cluster or the next word
                        if not opt[i + 1:] and words:
                            words.pop(0)
                        break
                if split_value is not None:
                    break
            if split_value is not None:
                try:
                    inner = shlex.split(split_value.replace("\\_", " "), posix=True)   # \_ is env's own space
                except ValueError:
                    inner = split_value.split()
                words = inner + words
                continue                       # re-strip wrappers on the surfaced command (nested env/sudo)
            while words and re.match(r"^\w+=", words[0]):
                words.pop(0)   # env NAME=VALUE assignments before the wrapped command
        elif wrapper in WRAPPER_VALUE_OPTS:
            value_opts = WRAPPER_VALUE_OPTS[wrapper]
            while words and words[0].startswith("-"):
                opt = words.pop(0)
                name = opt.split("=", 1)[0]
                if "=" not in opt and name in value_opts and words:
                    words.pop(0)
            if wrapper == "timeout" and words:
                words.pop(0)   # the duration
            while words and re.match(r"^\w+=", words[0]):
                words.pop(0)   # env NAME=VALUE assignments before the wrapped command
    return words


class Dir(str):
    """A working directory with what the hook knows about it. `known` is False after a cd it could not follow
    (`cd "$DIR"`, `cd -`, popd): the text is then only the last directory it knew. `seen` lists the directories
    the command has been in - the candidates for where it really is."""
    known = True
    seen = ()


def directory(path, seen, is_known=True):
    d = Dir(path)
    d.known = is_known
    d.seen = tuple(seen) + ((str(path),) if is_known else ())
    return d


def known(cwd):
    return getattr(cwd, "known", True)


def candidates(cwd, project):
    """The directories a command may be running in: the one the hook followed it to - or, after a cd it could
    not follow, every directory the command has been in and the project itself."""
    if known(cwd):
        return [str(cwd)]
    out = []
    for d in tuple(cwd.seen) + (str(cwd), project):
        if d not in out:
            out.append(d)
    return out


TOPLEVEL = re.compile(r"\$[(_]git rev-parse --show-toplevel[)_]|`git rev-parse --show-toplevel`")


def follow_cd(words, cwd, project, substitution=None):
    """`cd dir && ...` (pushd, popd): the commands that follow run there. The target is read the way the shell
    expands the forms the hook can know: ~, $HOME, $PWD, $CLAUDE_PROJECT_DIR and
    "$(git rev-parse --show-toplevel)". After any other expansion, `cd -` or popd the directory is not known,
    and what follows is judged against every directory the command has been in (see candidates).
    substitution: the parts that follow a bare `cd` whose target is an unquoted $( ... )."""
    if not words or words[0] not in ("cd", "pushd", "popd"):
        return cwd
    seen = list(getattr(cwd, "seen", ())) or [str(cwd)]
    operands = [w for w in words[1:] if w == "-" or not w.startswith(("-", "+"))]
    target = operands[0] if operands else None
    if substitution is not None:
        toplevel = substitution[1:2] == [")"] and words_of(substitution[0]) == ["git", "rev-parse", "--show-toplevel"]
        target = "$(git rev-parse --show-toplevel)" if toplevel else "$()"
    elif words[0] == "popd" or target == "-" or (target is None and len(words) > 1) or (target is None and words[0] == "pushd"):
        return directory(cwd, seen, False)      # an earlier directory, or a rotation of the directory stack
    elif target is None:
        target = "~"
    if TOPLEVEL.search(target):
        target = TOPLEVEL.sub(lambda m: git(str(cwd), "rev-parse", "--show-toplevel") or project, target)
    home = os.path.expanduser("~")
    target = expand(target, cwd, project).replace("${HOME}", home).replace("$HOME", home)
    if "$" in target or "`" in target or GLOB_CHARS.search(target):
        return directory(cwd, seen, False)
    if not os.path.isabs(target) and not known(cwd):
        return directory(os.path.join(cwd, target),
                         [os.path.realpath(os.path.join(c, target)) for c in candidates(cwd, project)], False)
    return directory(os.path.realpath(target if os.path.isabs(target) else os.path.join(cwd, target)), seen)


def walk(parts, cwd, project, end=None):
    """The simple commands of a split command line, each with its words and the directory it runs in: a cd is
    followed, and one made inside ( ... ), $( ... ) or backticks ends with them. The directory the command line
    ends in is appended to `end`."""
    stack = []
    for i, segment in enumerate(parts):
        if segment == "(":
            stack.append(cwd)
        elif segment == ")":
            if stack:
                cwd = stack.pop()
        else:
            words = words_of(segment)
            substituted = words in (["cd"], ["pushd"]) and parts[i + 1:i + 2] == ["("]
            cwd = follow_cd(words, cwd, project, parts[i + 2:i + 4] if substituted else None)
            yield segment, words, cwd
    if end is not None:
        end.append(cwd)


def short_flags(args):
    return "".join(a[1:] for a in args if a.startswith("-") and not a.startswith("--"))


def mentions(command, path):
    """The command names this path as a whole segment ('gauntlet' matches 'gauntlet/x', not 'gauntletFast');
    a name inside a Gradle output directory ('build/gauntlet/x.log') is not the protected path."""
    return re.search(r"(?<![\w.-])(?<!build/)(?<!\.gradle/)" + re.escape(path) + r"(?![\w-]|\.\w)", command,
                     re.IGNORECASE) is not None


def without_gradle_tasks(text):
    """`./gradlew gauntlet` names a task, not the directory: drop task names (bare words of a Gradle call that are
    neither options nor option values) before the text match; everything else of the command stays."""
    for segment in segments(text):
        words = words_of(segment)
        if not words or os.path.basename(words[0]) not in GRADLE:
            continue
        tasks = {w for i, w in enumerate(words[1:], 1)
                 if not w.startswith("-") and "/" not in w and words[i - 1] not in GRADLE_PATH_OPTIONS}
        cleaned = segment
        for task in tasks:
            cleaned = re.sub(r"(?<!\S)" + re.escape(task) + r"(?!\S)", " ", cleaned)
        text = text.replace(segment, cleaned, 1)     # only inside the Gradle call itself
    return text


def inline_code(words):
    if not words:
        return False
    cmd, args = os.path.basename(words[0]), words[1:]
    return (is_interpreter(cmd) or cmd == "perl") and bool(inline_code_strings(cmd, args))


def archive_mode(args):
    """What a tar command does: the first of t (list), x, c, r, u, A, d among its mode letters, or ''."""
    for k, a in enumerate(args):
        if a in ("--list", "--extract", "--get", "--create", "--append", "--update", "--concatenate", "--diff", "--compare"):
            return {"--list": "t", "--extract": "x", "--get": "x", "--create": "c", "--diff": "d", "--compare": "d"}.get(a, "r")
        if (k == 0 or a.startswith("-")) and not a.startswith("--"):
            mode = next((ch for ch in a.lstrip("-") if ch in "txcruAd"), "")
            if mode:
                return mode
    return ""


def writes_worktree(words, segment, raw=None):
    """raw: the command as written - quoted text in `segment` is neutralised, and perl's `...`, system(...),
    open(...) live inside quotes."""
    if not words:
        return False
    cmd, args = os.path.basename(words[0]), words[1:]
    if cmd == "tar":                                      # listing, comparing and writing to stdout change nothing
        return archive_mode(args) not in ("t", "d") and bool(archive_targets(args))
    if cmd == "unzip":                                    # -l -v -Z list, -t tests, -p prints to stdout, -z the comment
        return not any(a.startswith("-") and set(a[1:]) & set("lvZtpz") for a in args)
    if cmd in MUTATING_COMMANDS:
        return True
    if cmd == "sed":
        return "i" in short_flags(args) or "--in-place" in args
    if cmd == "perl":                      # code comes with -e / -E, -M / -m ('-MPOSIX;rmdir(...)') and -F (a pattern)
        return any(PERL_IN_PLACE.match(a) for a in args) or (bool(set(short_flags(args)) & set("eEMmF"))
                                                             and perl_writes(raw if raw is not None else segment))
    if cmd in ("awk", "gawk"):
        return "-i" in args or any(a.startswith("-i") for a in args)
    if cmd in SHELLS:
        return False                       # its -c payload is a command line of its own: see nested_violation
    if is_interpreter(cmd):
        return inline_code(words)
    if cmd == "git":
        index = git_subcommand_index(args)
        sub, rest = (args[index], args[index + 1:]) if index >= 0 else ("", [])
        if sub not in GIT_WORKTREE_WRITING or git_read_only(sub, rest):
            return False
        if sub == "restore":               # --staged without --worktree only takes changes out of the index
            staged = any(a in ("--staged", "-S") for a in rest)
            return not staged or any(a in ("--worktree", "-W") for a in rest)
        if sub == "reset":                 # only --hard, --merge and --keep touch the files
            return any(a in ("--hard", "--merge", "--keep") for a in rest)
        return True
    if cmd == "find":                      # it writes when it deletes, prints to a file or runs a command that writes
        if any(a in ("-delete", "-fprint", "-fprint0", "-fls", "-fprintf") for a in args):
            return True
        return any(writes_worktree(words_of(code), code) for code in inline_code_strings(cmd, args))
    return False


def perl_writes(text):
    """True when the perl command line `text` (as written) names a call that writes, in its program or in its other
    words (-M'POSIX;system(...)' is code too). The program is the one perl runs: the -e / -E texts, separate or
    attached (-esystem(...)), joined by newlines. Its match patterns are data - `perl -ne 'print if /`/'` and
    `perl -ne 'print if /open/'` only read."""
    try:
        words = shlex.split(text, posix=True)
    except ValueError:
        words = []
    start = next((k for k, w in enumerate(words) if os.path.basename(w) == "perl"), None)
    if start is None:
        return bool(PERL_WRITES.search(text))
    other, programs, i = words[:start + 1], [], start + 1
    while i < len(words):
        switch = PERL_PROGRAM_SWITCH.fullmatch(words[i])
        if switch and (switch.group(2) or i + 1 < len(words)):
            programs.append(switch.group(2) or words[i + 1])
            i += 1 if switch.group(2) else 2
        elif words[i].startswith("-") and words[i] not in ("-", "--"):
            other.append(words[i])
            i += 1
        else:
            other += words[i:]              # the script or the first file: perl reads no more switches
            break
    return bool(PERL_WRITES.search(" ".join(other)) or PERL_WRITES.search(perl_without_patterns("\n".join(programs))))


def perl_without_patterns(code):
    """perl's program without the text of its match patterns: a pattern is data, `print if /`/` and `s/system//`
    call nothing. Only what perl certainly reads as a pattern goes - a / where perl expects a term (see
    PERL_BEFORE_PATTERN), the pattern of m// and of s/// - and only when it runs no code (PERL_PATTERN_CODE).
    Strings, qr//, tr/// and the replacement of s/// (code with /e) stay. Whatever this reader does not follow
    exactly leaves the whole program as it is: a comment, ?, POD, a heredoc or <...> glob, ${...}, $$, $', a
    quote-like word after a sigil, -> or - (&s, ->s, -s, %m), a / after a variable (perl reads
    `print $fh /.../` as a pattern), after a word or after a }."""
    out, prev, i = [], "", 0
    while i < len(code):
        c = code[i]
        if c.isspace():
            out.append(c)
            i += 1
            continue
        if c in "#?" or (c == "=" and (i == 0 or code[i - 1] == "\n")):
            return code                                  # a comment, ?PATTERN?, POD
        word = PERL_WORD.match(code, i)
        if word:
            name, end = word.group(0), word.end()
            if name in ("sub", "format", "__END__", "__DATA__") or code.startswith("'", end):
                return code                              # a sub's name, a format, data, Foo'bar
            sigil = prev in ("%", "&", "*", "-", "->", "::")     # &s, %m, -s, ->s: a name, not an operator
            if name in PERL_QUOTE_LIKE:
                read = perl_quote_like(code, end, name)
                if read is None or sigil or prev in ("package", "use", "no", "require"):
                    return code                          # a name (use s, ->s), =>, $h{s}, a text never closed
                spans, end = read
                out.append(perl_pattern(code, i, end, spans[0]) if name in ("m", "s") else code[i:end])
                prev, i = "term", end
                continue
            out.append(name)
            prev, i = ("name" if sigil else name), end
            continue
        if c == "/" and prev in PERL_BEFORE_PATTERN:
            close = perl_closing(code, i)
            if close < 0:
                return code
            end = PERL_FLAGS.match(code, close + 1).end()
            out.append(perl_pattern(code, i, end, (i + 1, close)))
            prev, i = "term", end
            continue
        if c in "'\"`":
            close = perl_closing(code, i)
            if close < 0:
                return code
            out.append(code[i:close + 1])
            prev, i = "term", close + 1
            continue
        if c == "<":
            readline = PERL_READLINE.match(code, i)
            if not readline or prev not in PERL_BEFORE_PATTERN:
                return code                              # a heredoc, a glob, a comparison
            out.append(readline.group(0))
            prev, i = "term", readline.end()
            continue
        token = PERL_NUMBER.match(code, i) or PERL_VARIABLE.match(code, i) or PERL_OPERATOR.match(code, i)
        if not token or (c == "/" and prev != "term") or (c == "{" and prev in ("%", "&", "*")):
            return code                                  # a / after a variable or a word may open a pattern
        out.append(token.group(0))
        prev = ("term" if c.isdigit() or c in ")]" else "variable" if c in "$@" else token.group(0))
        i = token.end()
    return "".join(out)


def perl_quote_like(code, i, word):
    """([(start, end) of each delimited text], the end of the construct) of perl's quote-like `word` whose
    delimiters start at i - or None when it is not read exactly: =>, a hash key ($h{s}), a comment or a letter for a
    delimiter, a text that is never closed."""
    spans, k = [], i
    for part in range(2 if word in ("s", "tr", "y") else 1):
        if part == 0 or code[k] in "}])>":              # s{...}{...}: the replacement has delimiters of its own
            k += part
            while k < len(code) and code[k].isspace():
                k += 1
            if (k == len(code) or code[k] in "=)]}>:\\" or code[k] == "_" or code[k].isalnum()
                    or (code[k] == "#" and (part or k > i))):
                return None
        close = perl_closing(code, k)
        if close < 0:
            return None
        spans.append((k + 1, close))
        k = close                                        # s/.../.../: the middle delimiter is shared
    return spans, PERL_FLAGS.match(code, k + 1).end()


def perl_closing(code, i):
    """Index of the delimiter that closes the one at i, or -1: a bracket nests, any other character closes at its
    next unescaped occurrence."""
    opening = code[i]
    closing, depth, k = PERL_BRACKETS.get(opening, opening), 0, i + 1
    while k < len(code):
        if code[k] == "\\":
            k += 1
        elif code[k] == closing:
            if depth == 0:
                return k
            depth -= 1
        elif code[k] == opening:
            depth += 1
        k += 1
    return -1


def perl_pattern(code, start, end, span):
    """code[start:end] without the text of the pattern at span - unless interpolating that pattern can run code."""
    if PERL_PATTERN_CODE.search(re.sub(r"\\.", "", code[span[0]:span[1]], flags=re.S)):
        return code[start:end]
    return code[start:span[0]] + code[span[1]:end]


def changes_state(words, segment, cwd=None, ctx=None, raw=None):
    if words and os.path.basename(words[0]) == "git":
        sub = git_subcommand(words[1:])
        rest = words[words.index(sub) + 1:] if sub in words else []
        return sub in GIT_STATE_CHANGING and not git_read_only(sub, rest)
    if not writes_worktree(words, segment, raw):
        return False
    if ctx is not None and os.path.basename(words[0]) in MUTATING_COMMANDS:
        # everything it touches lies outside the project: tee /tmp/x.log, mkdir /tmp/review - the bare names it creates
        # too, where it runs (`mv /tmp/a notes.txt` in the project writes the project)
        tokens = path_tokens(words, cwd, ctx.project)
        if tokens and all(ctx.relative(expand(t, cwd, ctx.project), c) is None
                          for t in tokens + new_names(words, cwd) for c in candidates(cwd, ctx.project)):
            return False
    return True


def git_read_only(sub, rest):
    """`git branch -a`, `git stash list`, `git tag -l 'v*'`, `git tag --contains HEAD`: listing forms of otherwise
    state-changing commands. A word that is no option makes `git branch` and `git tag` create something - unless
    it is the value of a listing option or a pattern after -l / --list."""
    if sub in PLUMBING_READS:
        return plumbing_read_only(sub, rest)
    forms = GIT_READ_ONLY_FORMS.get(sub)
    if forms is None:
        return False
    if not rest:
        return sub != "stash"              # bare `git stash` saves; bare branch/tag/notes/submodule list
    if sub in ("stash", "worktree", "notes", "submodule"):
        return rest[0] in forms
    args = branch_options(rest)            # without the values of --sort, --format, --contains, --merged, ...
    listing = any(a in ("-l", "--list") for a in args)
    for a in args:
        if a.startswith("--"):
            ok = a.split("=", 1)[0] in forms
        elif a.startswith("-") and len(a) > 1:
            ok = set(a[1:]) <= set("larvi") or (sub == "tag" and re.fullmatch(r"-n\d*", a) is not None)
        else:
            ok = listing                   # a pattern
        if not ok:
            return False
    return True


# the plumbing of GIT_STATE_CHANGING that also has forms which only print
PLUMBING_READS = {"symbolic-ref", "hash-object", "read-tree", "merge-file", "sparse-checkout", "bisect"}


def plumbing_read_only(sub, rest):
    """The forms of that plumbing that write nothing (git's documentation, 2.43): symbolic-ref with one name prints
    the ref it points to (`git symbolic-ref --short HEAD`; -d deletes it, a second name or -m <reason> sets it),
    hash-object without -w only computes an id, read-tree -n only checks, merge-file -p prints the merge instead of
    writing it, `sparse-checkout list` and `check-rules` and `bisect log`, `view`, `visualize`, `terms` and `help`
    only print. A short option may sit in a cluster (`-qd`, `-wt blob`); what follows a letter that takes a value
    is that value."""
    options = rest[:rest.index("--")] if "--" in rest else rest
    clusters = [a[1:] for a in options if a.startswith("-") and not a.startswith("--") and len(a) > 1]

    def flag(letter, value_letters=""):
        for cluster in clusters:
            for ch in cluster:
                if ch == letter:
                    return True
                if ch in value_letters:
                    break
        return False
    if sub == "symbolic-ref":
        operands = [a for a in options if not a.startswith("-")] + (rest[rest.index("--") + 1:] if "--" in rest else [])
        return (len(operands) == 1 and not flag("d") and not flag("m")
                and not any(long_opt(a, "--delete", 3) for a in options))
    if sub == "hash-object":
        return not flag("w", "t")
    if sub == "read-tree":
        return "--dry-run" in options or flag("n")
    if sub == "merge-file":
        return "--stdout" in options or flag("p", "L")
    words = [a for a in rest if not a.startswith("-")]
    if sub == "sparse-checkout":
        return not words or words[0] in ("list", "check-rules")
    return not words or words[0] in ("log", "view", "visualize", "terms", "help")      # bisect


def git_subcommand_index(args):
    """Index of the subcommand in a git argument list, or -1. Global options are skipped together with the
    value of those that take it as the next argument: `git --namespace x commit --amend` is a commit."""
    skip = False
    for i, a in enumerate(args):
        if skip:
            skip = False
        elif a in GIT_GLOBAL_VALUE_OPTIONS:
            skip = True
        elif not a.startswith("-"):
            return i
    return -1


def git_subcommand(args):
    index = git_subcommand_index(args)
    return args[index] if index >= 0 else ""


def sed_files(args):
    """The file operands of sed. Its script - the first operand, unless -e or -f gives it - is not a path,
    although `s/a/b/` looks like one."""
    files, script, i = [], False, 0
    while i < len(args):
        a = args[i]
        if a.startswith("--"):
            script = script or a.split("=", 1)[0] in ("--expression", "--file")
            i += 1 if "=" in a or a.split("=", 1)[0] not in ("--expression", "--file") else 2
        elif a.startswith("-") and len(a) > 1:
            flags = a[1:]
            if not flags.startswith("i") and ("e" in flags or "f" in flags):   # -i.bak is a suffix, not flags
                script = True
                i += 1 if flags[-1] not in "ef" else 2      # -e SCRIPT / -ne SCRIPT: the script is the next word
            else:
                i += 1
        elif not script:
            script = True
            i += 1
        else:
            files.append(a)
            i += 1
    return files


def awk_files(args):
    """The file operands of awk: the program (the first operand, unless -f gives it) is not a path."""
    files, program, i = [], False, 0
    while i < len(args):
        a = args[i]
        if a in ("-f", "--file", "-v", "--assign", "-F", "--field-separator", "-i", "--include"):
            program = program or a in ("-f", "--file")
            i += 2
        elif a.startswith("-") and len(a) > 1:
            program = program or a.startswith(("-f", "--file="))
            i += 1
        elif not program:
            program = True
            i += 1
        else:
            files.append(a)
            i += 1
    return files


def find_roots(args):
    """Where find searches (`.` when nothing is named) and the files its -fprint actions write. The tests of
    its expression (`-name '*.java'`) are not paths, and the command of -exec is judged as a command."""
    roots, i = [], 0
    while i < len(args) and args[i] in ("-H", "-L", "-P"):
        i += 1
    while i < len(args) and not args[i].startswith("-") and args[i] not in ("(", ")", "!", ","):
        roots.append(args[i])
        i += 1
    written = [args[k + 1] for k in range(i, len(args) - 1) if args[k] in ("-fprint", "-fprint0", "-fls", "-fprintf")]
    return (roots or ["."]) + written


def copy_destination(args):
    """What cp / install / rsync / ln write: the last operand, or the directory of -t / --target-directory. The
    sources are only read - copying a config out of the project changes nothing in it."""
    operands, target, skip = [], None, False
    for i, a in enumerate(args):
        if skip:
            skip = False
        elif a in ("-t", "--target-directory"):
            target, skip = (args[i + 1] if i + 1 < len(args) else None), True
        elif a.startswith("--target-directory="):
            target = a.split("=", 1)[1]
        elif not a.startswith("-") or a == "-":
            operands.append(a)
    return [target] if target else operands[-1:]


def archive_targets(args):
    """What tar writes: the archive when it creates or appends; when it extracts, the directory of -C (or the
    current one) - or the members named after the archive within it. The archive it reads is no target."""
    archive, base, operands, skip = None, None, [], False
    for k, a in enumerate(args):
        following = args[k + 1] if k + 1 < len(args) else None
        if skip:
            skip = False
        elif a.startswith("--file="):
            archive = a.split("=", 1)[1]
        elif a.startswith("--directory="):
            base = a.split("=", 1)[1]
        elif a in ("--file", "--directory", "-C"):
            archive, base, skip = (following if a == "--file" else archive), (base if a == "--file" else following), True
        elif (k == 0 or a.startswith("-")) and not a.startswith("--") and a.endswith("f"):
            archive, skip = following, True     # the archive follows a bundle of flags that ends in f
        elif not a.startswith("-") and k > 0:
            operands.append(a)
    if archive_mode(args) in ("c", "r", "u", "A"):
        return [archive] if archive and archive != "-" else []
    return [os.path.join(base or ".", member) for member in operands] or [base or "."]


def unzip_targets(args):
    """Where unzip extracts: the directory of -d, or the current one. The archive is only read."""
    for k, a in enumerate(args):
        if a == "-d" and k + 1 < len(args):
            return [args[k + 1]]
        if a.startswith("-d") and len(a) > 2:
            return [a[2:]]
    return ["."]


OPERANDS = {"sed": sed_files, "awk": awk_files, "gawk": awk_files, "find": find_roots, "tar": archive_targets,
            "unzip": unzip_targets, "cp": copy_destination, "install": copy_destination, "rsync": copy_destination,
            "ln": copy_destination}
# a word that is a directory the hook knows (expand): `rm -rf "$CLAUDE_PROJECT_DIR"` and `rm -rf $PWD` name the project
KNOWN_DIRECTORY = re.compile(r"\$(?:CLAUDE_PROJECT_DIR|PWD|\{CLAUDE_PROJECT_DIR\}|\{PWD\})")


def path_tokens(words, cwd, project):
    """Path-like arguments of a command, with `git -C dir` honoured and globs expanded."""
    if not words:
        return []
    cmd, args = os.path.basename(words[0]), words[1:]
    base = cwd
    if cmd in OPERANDS:
        args = OPERANDS[cmd](args)          # a sed script, an awk program and a find test are not paths
    if cmd == "git" and "-C" in args and args.index("-C") + 1 < len(args):
        target = args[args.index("-C") + 1]
        base = os.path.realpath(target if os.path.isabs(target) else os.path.join(cwd, target))
    tokens = []
    for a in args:
        if a.startswith("-") and not a.startswith("-/"):
            continue
        if GLOB_CHARS.search(a):
            pattern = a if os.path.isabs(a) else os.path.join(base, a)
            tokens.extend(glob.glob(pattern) or [a])
        elif "/" in a or a in (".", "..") or KNOWN_DIRECTORY.fullmatch(a) or os.path.exists(os.path.join(base, a)):
            tokens.append(a)
    if base == cwd:
        return tokens
    return [t if os.path.isabs(t) else os.path.join(base, t) for t in tokens]   # git -C dir: resolve against dir


def written_words(words):
    """The operands a writing command may write - the written ones of sed, cp, tar, ... (OPERANDS), every other
    command's all - also a name that does not exist yet, which path_tokens leaves out."""
    if not words:
        return []
    cmd, args = os.path.basename(words[0]), words[1:]
    if cmd == "dd":
        return [a[3:] for a in args if a.startswith("of=")]      # it writes the file of of=, reads the one of if=
    if cmd in OPERANDS:
        args = OPERANDS[cmd](args)
    return [a for a in args if a and not a.startswith("-")]


def unseen(words, cwd):
    """The written operands path_tokens leaves out: a bare name that does not exist yet (`mv /tmp/x AGENTS.md`) and
    the file of dd's of=, which it takes for a name that starts with `of=` - their statement is matched as text."""
    dd = bool(words) and os.path.basename(words[0]) == "dd"
    return [w for w in written_words(words) if dd or ("/" not in w and w not in (".", "..") and not GLOB_CHARS.search(w)
                                                      and not os.path.exists(os.path.join(str(cwd), w)))]


# ----------------------------------------------------------------------------- the read-only roles outside the project

# The reviewer and the checker may write outside the project, and only where the guard reads the path as written. A
# variable, a substitution or ~ is a value the same line may set anywhere - `P=<project>; cd /tmp && cp x $P/A.java`,
# `cd /tmp && PWD=<project>; ... > $PWD/A.java`, `HOME=<project>; ... > ~/A.java` -, and a value that starts outside
# can still lead back in (`/tmp/$X` with X=../<project>/...): no such word is a place outside the project.
UNEXPANDED = re.compile(r"[$`]")
SINGLE_QUOTED = re.compile(r"'[^']*'")
# the commands that create the bare name they write (`mv /tmp/x notes.txt` makes notes.txt where it runs), and the
# options of the writing commands whose value is the next word and no file they write
CREATING = {"mv", "cp", "install", "ln", "rsync", "touch", "mkdir", "tee", "truncate", "dd"}
WRITE_VALUE_OPTIONS = {"touch": ("-d", "-t", "-r", "--date", "--reference"),
                       "truncate": ("-s", "-r", "--size", "--reference"), "mkdir": ("-m", "--mode"),
                       "mv": ("-S", "--suffix")}


def unexpanded_word(word):
    """True when bash would expand the word: a variable or a substitution outside single quotes, a leading ~."""
    return bool(UNEXPANDED.search(SINGLE_QUOTED.sub("", word))) or word.startswith("~")


def unexpanded_redirect(neutral):
    """The first redirection of the neutralised command line whose file the guard does not expand (`> "$tmp/x"`,
    `> $(mktemp)`, `> /tmp/run-$(date +%s).log`, `` > `mktemp` ``, `> ~/x`), or None. A descriptor (`>&2`,
    `2>&"$saved_stderr"`) is no file, and an escaped `\\>` is a character."""
    for m in REDIRECT.finditer(neutral):
        word = HERE_WORD.match(neutral, m.end()).group(1)
        if not word or not unescaped(neutral, m.start()):
            continue                            # >(...) is a process substitution, judged as the code it runs
        try:
            plain = "".join(shlex.split(word))
        except ValueError:
            plain = word
        if m.group(1) == ">&" and (FD_WORD.fullmatch(plain) or into_descriptor(neutral, m.start())):
            continue
        if unexpanded_word(word):
            return word
    return None


def before_substitution(neutral):
    """The simple commands of the neutralised command line whose words go on into a command substitution: segments()
    ends `cp /tmp/x $(echo dir)/A.java` where the substitution starts, so the destination is not among its words."""
    found, last, backtick = set(), 0, False
    for m in separators(neutral):
        sep = m.group(0)
        if sep == "$(" or (sep == "`" and not backtick):
            found.add(neutral[last:m.start()])
        if sep == "`":
            backtick = not backtick
        last = m.end()
    return found


def hidden_write(words, substituted):
    """The written word of a writing command that the guard does not expand, for the read-only roles: a variable, a
    substitution or ~ in it (`cp /tmp/x $P/A.java`, `mv /tmp/a "$X"`, `dd of=$X`), or the substitution its words go on
    into (`cp /tmp/x $(echo dir)/A.java`). None when every written word is as written."""
    if not words or os.path.basename(words[0]) not in MUTATING_COMMANDS:
        return None
    if substituted:
        return "$( ... )"
    return next((w for w in written_files(words) if unexpanded_word(w)), None)


def written_files(words):
    """written_words without the value of an option such as touch -d or mkdir -m, which names no file."""
    cmd, args = os.path.basename(words[0]), words[1:]
    if cmd not in WRITE_VALUE_OPTIONS:
        return written_words(words)
    names, skip = [], False
    for a in args:
        if skip:
            skip = False
        elif a in WRITE_VALUE_OPTIONS[cmd]:
            skip = True
        elif a and not a.startswith("-"):
            names.append(a)
    return names


def new_names(words, cwd):
    """The bare names a creating command writes that do not exist yet (`mv /tmp/x notes.txt`, `touch /tmp/a b.txt`):
    path_tokens leaves them out, and they land in the directory the command runs in."""
    if os.path.basename(words[0]) not in CREATING:
        return []
    return [w for w in written_files(words) if "/" not in w and w not in (".", "..") and not GLOB_CHARS.search(w)
            and not os.path.exists(os.path.join(str(cwd), w))]


# ----------------------------------------------------------------------------- helpers

PROPERTY = re.compile(r"([^=:\s]+)\s*[=:]?\s*(.*)$")


def read_properties(path):
    """The settings of gauntlet.properties with the separators java.util.Properties reads (gauntlet.gradle loads
    the same file with it): `key=value`, `key: value` and `key value`; `#` and `!` start a comment line."""
    props = {}
    try:
        with open(path, encoding="utf-8") as f:
            for line in f:
                m = PROPERTY.match(line.strip())
                if m and line.strip()[0] not in "#!":
                    props[m.group(1)] = m.group(2).strip()
    except OSError:
        pass
    return props


def git(project, *args):
    try:
        return subprocess.run(["git", "-C", project, "-c", "core.quotePath=false", *args], stdout=subprocess.PIPE,
                              stderr=subprocess.DEVNULL, text=True, timeout=30).stdout.strip()
    except (OSError, subprocess.TimeoutExpired):
        return ""


def git_code(project, *args):
    try:
        return subprocess.run(["git", "-C", project, *args], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                              timeout=30).returncode
    except (OSError, subprocess.TimeoutExpired):
        return -1


if __name__ == "__main__":
    sys.exit(main())

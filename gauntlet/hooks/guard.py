#!/usr/bin/env python3
"""PreToolUse guard for the gauntlet (Claude Code hook).

Keeps agents from changing their own constraints:
  1. nobody edits the gauntlet itself (gauntlet/, .claude/, CLAUDE.md, the CI workflow) or the user-level
     Claude Code settings (~/.claude/), which could switch the hooks off;
  2. only the `specifier` subagent writes the Gherkin specification, and once the specification is
     committed on the branch (= approved by the human in the chat) it is frozen for every agent;
  3. role scopes: specifier -> specs only, hardener -> test code only, reviewer -> read-only;
  4. human-only actions are refused: merging the PR, rewriting or re-pointing the branch (--amend,
     rebase, force push, reset/checkout -B/branch -f to another revision), pushing to the base branch,
     --no-verify, switching the hooks off.

Shell commands are checked heuristically: every path-like token of a command that writes to the working
tree is resolved (following `cd` and `git -C`, expanding globs) and compared with the protected paths.
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
import subprocess
import sys

HUMAN_OWNED = ("gauntlet/", ".claude/", "CLAUDE.md", ".github/workflows/gauntlet.yml",
               ".github/CODEOWNERS", ".gitlab/CODEOWNERS", "CODEOWNERS")
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
# ... and the ones that only change git state (forbidden to the read-only reviewer, fine for others)
GIT_STATE_CHANGING = GIT_WORKTREE_WRITING | {"commit", "add", "push", "tag", "branch", "update-ref", "replace",
                                             "filter-branch", "filter-repo", "notes", "worktree", "submodule"}
GIT_READ_ONLY_FORMS = {"branch": ("--show-current", "--list", "-l", "-a", "-r", "-v", "-vv", "--contains", "--merged",
                                  "--no-merged", "--points-at", "--format", "--sort"),
                       "stash": ("list", "show"), "tag": ("--list", "-l", "-n", "--contains", "--points-at"),
                       "worktree": ("list",), "notes": ("list", "show"), "submodule": ("status",)}
HISTORY_REWRITING = {"rebase", "filter-branch", "filter-repo", "update-ref", "replace", "revert"}
INTERPRETERS = {"python", "python3", "node", "ruby", "bash", "sh", "zsh"}   # with -c / -e inline code
WRAPPERS = {"sudo", "command", "exec", "nohup", "time", "env", "timeout", "nice"}
PERL_WRITES = re.compile(r"\b(open|unlink|rename|system|exec|qx|mkdir|rmdir|truncate|chmod|chown|symlink|link|"
                         r"utime|syswrite|sysopen)\b|`")
REDIRECT = re.compile(r"(?<!>)>>?\|?\s*([^\s;&|<>()]+)")
GLOB_CHARS = re.compile(r"[*?\[]")


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
        reason = user_config_violation(path) or path_violation(agent, relative(path, project), ctx)
    elif tool == "Bash":
        reason = command_violation(agent, params.get("command", ""), ctx)
    else:
        reason = None
    if reason:
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": "[gauntlet guard] " + reason}}))
    return 0


class Context:
    """Where the specification lives, which branch is the base, and whether the spec is frozen."""

    def __init__(self, project):
        self.project = project
        props = read_properties(os.path.join(project, PROPERTIES))
        # module = the Gradle build directories inside a monorepo, comma-separated ("" = the repository root)
        self.modules = [m.strip().strip("/") for m in props.get("module", "").split(",") if m.strip()]
        prefixes = [m + "/" for m in self.modules] or [""]
        features_rel = props.get("acceptance.features", DEFAULT_FEATURES).strip("/") + "/"
        self.features_dirs = tuple(pfx + features_rel for pfx in prefixes)
        self.test_code = tuple(pfx + p for pfx in prefixes for p in TEST_CODE)
        self.src_main = [pfx + "src/main" for pfx in prefixes]
        self.build_dirs = tuple(pfx + d for pfx in prefixes for d in ("build/", ".gradle/")) + ("build/", ".gradle/")
        self.base_ref = props.get("baseRef", DEFAULT_BASE)
        self.base_branch = self.base_ref.split("/", 1)[1] if "/" in self.base_ref else self.base_ref
        self.approval = props.get("spec.approval", "chat").lower()
        self._frozen = None

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

    def role_scope(self, agent):
        return {"specifier": self.features_dirs, "hardener": self.test_code, "reviewer": ()}.get(agent)

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
    return any(rel == p.rstrip("/") or rel.startswith(p if p.endswith("/") else p + "/") for p in prefixes)


def contains(rel, prefixes):
    """The path is a parent of a protected path (rm -rf src takes the features with it)."""
    if rel == ".":
        return True
    return any(p.rstrip("/").startswith(rel + "/") for p in prefixes)


def user_config_violation(path):
    if not path:
        return None
    home_claude = os.path.realpath(os.path.expanduser("~/.claude"))
    absolute = os.path.realpath(os.path.expanduser(path)) if os.path.isabs(os.path.expanduser(path)) else None
    if absolute and (absolute == home_claude or absolute.startswith(home_claude + os.sep)):
        return "~/.claude is the user's Claude Code configuration (it could switch the hooks off). Do not touch it."
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

def command_violation(agent, command, ctx):
    reason = human_only(command, ctx)
    if reason:
        return reason
    scope = ctx.role_scope(agent)
    read_only = scope is not None and not scope
    protected = ctx.protected(agent)
    command = strip_heredocs(command)       # a heredoc body is data, not commands
    neutral = QUOTED.sub(lambda m: re.sub(r"[>;&|()]", "_", m.group(0)), command)  # '>' in quotes is text
    cwd = ctx.project
    for segment in segments(neutral):
        words = words_of(segment)
        cwd = follow_cd(words, cwd, ctx.project)
        for target in REDIRECT.findall(segment):
            reason = write_violation(agent, target, cwd, ctx, protected, read_only, scope, "writing " + target)
            if reason:
                return reason
        if read_only and changes_state(words, segment):
            return f"The {agent} is read-only: no file or git state changes. Report findings instead."
        if not writes_worktree(words, segment):
            continue
        tokens = [] if inline_code(words) else path_tokens(words, cwd, ctx.project)
        for token in tokens:
            reason = write_violation(agent, token, cwd, ctx, protected, read_only, scope, "this command")
            if reason:
                return reason
        # paths the tokens cannot resolve (`python -c ...`, $VAR, `...`, xargs, no path at all): match the text -
        # the whole command for inline code, otherwise outside quotes (a commit or stash message is not a path)
        if not tokens or unresolvable(segment, command):
            text = command if inline_code(words) else QUOTED.sub("''", command)
            hit = next((p for p in protected if mentions(text, p)), None)
            if hit:
                return protected_reason(agent, hit, ctx)
    return None


def strip_heredocs(command):
    """Drop heredoc bodies (`<<EOF` ... `EOF`): they are text, and `->` or `rm` inside them are not commands."""
    lines = command.split("\n")
    out, delimiters = [], []
    for line in lines:
        if delimiters:
            if line.strip() == delimiters[0]:
                delimiters.pop(0)
            continue
        out.append(line)
        delimiters += [m.group(2) for m in re.finditer(r"<<-?\s*(['\"]?)(\w+)\1", line)]
    return "\n".join(out)


def unresolvable(segment, command):
    return "$" in command or "`" in command or re.search(r"(^|[\s|;&])xargs\s", segment) is not None


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
    reason = user_config_violation(target)
    if reason:
        return reason
    rel = relative(target, ctx.project, cwd)
    if rel is None:
        return None
    if read_only:
        return f"The {agent} is read-only. Report findings instead of {what}."
    if is_under(rel, ctx.build_dirs):
        return None                     # Gradle outputs: any role may clean or write them
    hit = next((p for p in protected if is_under(rel, (p,)) or contains(rel, (p,))), None)
    if hit:
        return protected_reason(agent, hit, ctx)
    if scope and not is_under(rel, scope) and rel != ".":
        return f"The {agent} may only change files under {', '.join(scope)} ({what} touches {rel})."
    return None


def protected_reason(agent, hit, ctx):
    if ctx.is_spec(hit) and ctx.frozen:
        return frozen_reason(hit)
    return (f"This command could modify {hit}, which the {agent} agent must not change. "
            f"Human-owned files and the specification change only through the human.")


def human_only(command, ctx):
    if HOOK_SWITCH.search(QUOTED.sub("''", command)):
        return "Switching the gauntlet hooks off is reserved for the human."
    for segment in segments(command):
        words = words_of(segment)
        if len(words) < 2:
            continue
        cmd, args = os.path.basename(words[0]), words[1:]
        if cmd == "git":
            reason = git_human_only(args, ctx)
            if reason:
                return reason
        elif cmd == "gh":
            reason = gh_human_only(args)
            if reason:
                return reason
    return None


def gh_human_only(args):
    rest = list(args)
    while rest and rest[0].startswith("-"):          # global options: -R/--repo <owner/repo>, --hostname <host>
        opt = rest.pop(0)
        if opt in ("-R", "--repo", "--hostname") and rest:
            rest.pop(0)
    if rest[:2] == ["pr", "merge"] or rest[:1] == ["merge"]:
        return "Merging the pull request is the human's decision. Push the branch, open the PR and report."
    if rest[:1] == ["api"] and any("merge" in a.lower() for a in rest[1:]):
        return "Merging through the GitHub API is reserved for the human."
    if rest[:1] == ["repo"] and rest[1:2] and rest[1] in ("delete", "edit", "rename", "archive"):
        return "Changing the repository itself is reserved for the human."
    return None


def git_human_only(args, ctx):
    sub = git_subcommand(args)
    rest = args[args.index(sub) + 1:] if sub in args else []
    if skips_git_hooks(args):
        return "Skipping git hooks (--no-verify) is reserved for the human."
    if sub in HISTORY_REWRITING:
        return (f"git {sub} rewrites or undoes history, which may drop the approved specification commit. "
                f"Reserved for the human (fix forward with a new commit instead).")
    if sub == "commit" and "--amend" in rest:
        return "git commit --amend rewrites history (the approved spec commit must stay as it is). Make a new commit."
    if sub == "push":
        if any(a.startswith("--force") or (a.startswith("-") and not a.startswith("--") and "f" in a[1:])
               for a in rest):
            return "Force-pushing is reserved for the human."
        refspecs = [a for a in rest if not a.startswith("-")][1:]   # after the remote name
        if any(a.startswith("+") or a == ctx.base_branch or a.endswith(":" + ctx.base_branch)
               or a.endswith("/" + ctx.base_branch) for a in refspecs):
            return f"Pushing to {ctx.base_branch} is the human's decision (merge the PR). Push your own branch only."
    if sub == "reset":
        before_dashes = rest[:rest.index("--")] if "--" in rest else rest
        positional = [a for a in before_dashes if not a.startswith("-")]
        revision = positional[0] if positional else None
        # `git reset`, `git reset HEAD file`, `git reset file` unstage; anything else moves the branch
        if revision not in (None, "HEAD") and not os.path.exists(os.path.join(ctx.project, revision)):
            return "git reset to another revision drops commits (possibly the approved spec). Reserved for the human."
    if sub in ("checkout", "switch") and any(a in ("-B", "-C", "--force-create") for a in rest):
        return "Re-creating the branch at another revision drops its commits (the approved spec). Reserved for the human."
    if sub == "branch" and any(a in ("-f", "--force", "-D", "-M", "-d", "-m", "--delete", "--move", "--copy", "-c", "-C")
                               or (a.startswith("-") and not a.startswith("--") and any(ch in "fDMdmcC" for ch in a[1:]))
                               for a in rest):
        return "Moving, forcing or deleting branches is reserved for the human."
    return None


def skips_git_hooks(args):
    if "--no-verify" in args:
        return True
    if git_subcommand(args) != "commit":
        return False
    skip = False
    for a in args:
        if skip:
            skip = False
        elif a in GIT_VALUE_OPTIONS:
            skip = True
        elif a.startswith("-") and not a.startswith("--"):
            for i, flag in enumerate(a[1:]):      # short-option cluster such as -an or -am "msg"
                if flag == "n":
                    return True
                if flag in "mFCct":               # takes a value: attached ("-mfix") or the next argument
                    skip = i == len(a) - 2
                    break
    return False


def segments(command):
    return [s for s in re.split(r"\|\||&&|[;|&`\n]|\$\(|[()]", command) if s.strip()]


def words_of(segment):
    try:
        words = shlex.split(segment, posix=True)
    except ValueError:
        words = segment.split()
    while words and (re.match(r"^\w+=", words[0]) or words[0] in WRAPPERS or words[0] == "xargs"):
        wrapper = words.pop(0)
        if wrapper in ("xargs", "timeout", "nice"):
            while words and words[0].startswith("-"):
                words.pop(0)
            if wrapper == "timeout" and words:
                words.pop(0)   # the duration
    return words


def follow_cd(words, cwd, project):
    """`cd dir && ...` (or pushd): later segments resolve relative to dir."""
    if words and words[0] in ("cd", "pushd"):
        target = words[1] if len(words) > 1 and not words[1].startswith("-") else "~"
        target = os.path.expanduser(target)
        return os.path.realpath(target if os.path.isabs(target) else os.path.join(cwd, target))
    return cwd


def short_flags(args):
    return "".join(a[1:] for a in args if a.startswith("-") and not a.startswith("--"))


def mentions(command, path):
    """The command names this path as a whole segment ('gauntlet' matches 'gauntlet/x', not 'gauntletFast')."""
    return re.search(r"(?<![\w.-])" + re.escape(path) + r"(?![\w-])", command) is not None


def inline_code(words):
    if not words:
        return False
    cmd, args = os.path.basename(words[0]), words[1:]
    if cmd in INTERPRETERS:
        return any(f in short_flags(args) for f in "ce")
    return cmd == "perl" and "e" in short_flags(args).lower()


def writes_worktree(words, segment):
    if not words:
        return False
    cmd, args = os.path.basename(words[0]), words[1:]
    if cmd in MUTATING_COMMANDS:
        return True
    if cmd == "sed":
        return "i" in short_flags(args) or "--in-place" in args
    if cmd == "perl":
        return "i" in short_flags(args) or ("e" in short_flags(args).lower() and bool(PERL_WRITES.search(segment)))
    if cmd in ("awk", "gawk"):
        return "-i" in args or any(a.startswith("-i") for a in args)
    if cmd in INTERPRETERS:
        return any(f in short_flags(args) for f in "ce")
    if cmd == "git":
        return git_subcommand(args) in GIT_WORKTREE_WRITING
    if cmd == "find":
        if "-delete" in args:
            return True
        return any(a in ("-exec", "-execdir", "-ok") and i + 1 < len(args) and os.path.basename(args[i + 1])
                   in MUTATING_COMMANDS for i, a in enumerate(args))
    return False


def changes_state(words, segment):
    if words and os.path.basename(words[0]) == "git":
        sub = git_subcommand(words[1:])
        rest = words[words.index(sub) + 1:] if sub in words else []
        return sub in GIT_STATE_CHANGING and not git_read_only(sub, rest)
    return writes_worktree(words, segment)


def git_read_only(sub, rest):
    """`git branch -a`, `git stash list`, `git tag -l`: listing forms of otherwise state-changing commands."""
    forms = GIT_READ_ONLY_FORMS.get(sub)
    if forms is None:
        return False
    if not rest:
        return sub != "stash"              # bare `git stash` saves; bare branch/tag/notes/submodule list
    if sub in ("stash", "worktree", "notes", "submodule") or rest[0] in ("-l", "--list"):
        return rest[0] in forms or rest[0] in ("-l", "--list")
    return all(a in forms or a.startswith(("--format", "--sort", "--contains", "--points-at", "--merged", "--no-merged"))
               or (a.startswith("-") and not a.startswith("--") and set(a[1:]) <= set("larvn")) for a in rest)


def git_subcommand(args):
    skip = False
    for a in args:
        if skip:
            skip = False
            continue
        if a in ("-C", "-c", "--git-dir", "--work-tree"):
            skip = True
            continue
        if not a.startswith("-"):
            return a
    return ""


def path_tokens(words, cwd, project):
    """Path-like arguments of a command, with `git -C dir` honoured and globs expanded."""
    if not words:
        return []
    cmd, args = os.path.basename(words[0]), words[1:]
    base = cwd
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
        elif "/" in a or a in (".", "..") or os.path.exists(os.path.join(base, a)):
            tokens.append(a)
    if base == cwd:
        return tokens
    return [t if os.path.isabs(t) else os.path.join(base, t) for t in tokens]   # git -C dir: resolve against dir


# ----------------------------------------------------------------------------- helpers

def read_properties(path):
    props = {}
    try:
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, value = line.split("=", 1)
                    props[key.strip()] = value.strip()
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

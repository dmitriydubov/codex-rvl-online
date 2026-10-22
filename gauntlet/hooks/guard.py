#!/usr/bin/env python3
"""PreToolUse guard for the gauntlet (Claude Code hook).

Keeps agents from changing their own constraints:
  1. nobody edits the gauntlet itself (gauntlet/, .claude/, CLAUDE.md, the CI workflow) or the user-level
     Claude Code settings (~/.claude/), which could switch the hooks off;
  2. only the `specifier` subagent writes the Gherkin specification, and once the specification is
     committed on the branch (= approved by the human in the chat) it is frozen for every agent: no edit of
     it, and no commit that carries a change to it, however the files were changed (a script, a patch);
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
# options of a wrapper (and of xargs) that take the next word as their value: `nice -n 5 rm` runs rm, not 5
WRAPPER_VALUE_OPTIONS = {
    "env": {"-u", "--unset", "-C", "--chdir", "-S", "--split-string"},
    "timeout": {"-s", "--signal", "-k", "--kill-after"},
    "nice": {"-n", "--adjustment"},
    "sudo": {"-u", "--user", "-g", "--group", "-C", "--close-from", "-D", "--chdir", "-p", "--prompt", "-r", "--role",
             "-t", "--type", "-T", "--command-timeout", "-U", "--other-user", "-R", "--chroot"},
    "exec": {"-a"},
    "xargs": {"-I", "-n", "-P", "-L", "-d", "-E", "-s", "-a", "--arg-file", "--delimiter", "--max-args",
              "--max-procs", "--max-lines", "--max-chars", "--eof"},
}
SHELLS = {"bash", "sh", "zsh"}
SHELL_KEYWORDS = {"if", "then", "else", "elif", "do", "while", "until", "!", "{"}   # `then rm x` runs rm
GRADLE = {"gradlew", "gradle"}
GRADLE_PATH_OPTIONS = {"-p", "--project-dir", "-g", "--gradle-user-home", "--project-cache-dir", "-b", "--build-file",
                       "-c", "--settings-file", "-I", "--init-script", "--include-build"}
PERL_WRITES = re.compile(r"\b(open|unlink|rename|system|exec|qx|mkdir|rmdir|truncate|chmod|chown|symlink|link|"
                         r"utime|syswrite|sysopen)\b|`")
# GitHub API calls that merge: REST merge endpoints and GraphQL merge mutations - not any text with "merge" in it
MERGE_API = re.compile(r"/pulls/[^/\s]+/merge(?![\w-])|(^|/)repos/[^/\s]+/[^/\s]+/merges(?![\w-])|/merge-upstream(?![\w-])|"
                       r"\b(mergePullRequest|enablePullRequestAutoMerge|enqueuePullRequest|mergeBranch)\b")
BUILD_LOCK = ".gradle/gauntlet-build.lock"
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
        reason = command_violation(agent, params.get("command", ""), ctx, event.get("cwd"))
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
        self.build_locks = tuple(pfx + BUILD_LOCK for pfx in prefixes)
        self.base_ref = props.get("baseRef", DEFAULT_BASE)
        self.base_branch = self.base_ref.split("/", 1)[1] if "/" in self.base_ref else self.base_ref
        self.approval = props.get("spec.approval", "chat").lower()
        self._frozen = None

    @property
    def frozen(self):
        """True once the branch holds a commit that touches the feature files and they differ from the
        base branch: the human approved the specification in the chat and the agent committed it. From
        then on only the human changes it. Once the base branch has the same feature files (the PR was
        merged) the branch is free again. Fails open (not frozen) when the base branch cannot be found.
        A stale remote-tracking base (the branch was cut from a newer base than the local ref) makes the base's
        own spec commits look like the branch's, so before answering "frozen" the base is fetched once and
        checked again; when the fetch fails the answer stays "frozen"."""
        if self._frozen is None:
            if self.approval == "pr":
                self._frozen = False    # spec.approval=pr: the human reads the spec in the PR, no freeze
            elif git_code(self.project, "symbolic-ref", "-q", "HEAD") != 0:
                self._frozen = True     # detached HEAD: not the task's branch, nothing may be specified here
            else:
                self._frozen = self._spec_committed() and (not self._refresh_base() or self._spec_committed())
        return self._frozen

    def _spec_committed(self):
        specs = [d.rstrip("/") for d in self.features_dirs]   # one task per branch: any module's spec freezes all
        count = git(self.project, "rev-list", "--count", f"{self.base_ref}..HEAD", "--", *specs)
        differs = git_code(self.project, "diff", "--quiet", self.base_ref, "HEAD", "--", *specs) == 1
        return count.isdigit() and int(count) > 0 and differs

    def _refresh_base(self):
        """Fetches the base branch into its remote-tracking ref; False when there is no remote or the fetch failed."""
        if "/" not in self.base_ref:
            return False
        remote = self.base_ref.split("/", 1)[0]
        refspec = f"+refs/heads/{self.base_branch}:refs/remotes/{remote}/{self.base_branch}"
        return git_code(self.project, "fetch", "--quiet", remote, refspec) == 0

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
        return path in self.spec_dirs

    @property
    def spec_dirs(self):
        return [d.rstrip("/") for d in self.features_dirs]


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

def command_violation(agent, command, ctx, shell_cwd=None):
    command = with_nested_commands(command)
    reason = human_only(command, ctx) or frozen_spec_commits(command, ctx, shell_cwd or ctx.project)
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
        # paths the tokens cannot resolve (`python -c ...`, $VAR, `...`, xargs, no path at all): match the text
        # without Gradle task names (`./gradlew gauntlet` next to `python3 -c` names a task, not the directory) -
        # the whole command for inline code, otherwise outside quotes (a commit or stash message is not a path)
        if not tokens or unresolvable(segment, command):
            text = without_gradle_tasks(command if inline_code(words) else QUOTED.sub("''", command))
            hit = next((p for p in protected if mentions(text, p)), None)
            if hit:
                return protected_reason(agent, hit, ctx)
            if os.path.basename(BUILD_LOCK) in text:
                return lock_reason()
    return None


def with_nested_commands(command, depth=3):
    """`eval "rm -rf x"` and `bash -c "git push --force"` run their argument as a command: append it as a line of
    its own, so every check below sees it like any other command (nested ones too, a few levels deep)."""
    nested = []
    for segment in segments(strip_heredocs(command)):
        words = words_of(segment)
        if not words:
            continue
        cmd = os.path.basename(words[0])
        if cmd == "eval":
            nested.append(" ".join(words[1:]))
        elif cmd in SHELLS and "c" in short_flags(words[1:]):
            code = next((w for w in words[1:] if not w.startswith("-")), "")
            nested.append(code)
    nested = [n for n in nested if n.strip()]
    if not nested:
        return command
    inner = "\n".join(nested)
    return command + "\n" + (with_nested_commands(inner, depth - 1) if depth > 1 else inner)


def lock_reason():
    return (f"{BUILD_LOCK} keeps two Gradle runs out of one build directory; removing it lets a second run overwrite "
            f"the results the first one is judging. Wait for the other run to finish instead.")


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
    if any(is_under(rel, (lock,)) or contains(rel, (lock,)) for lock in ctx.build_locks):
        return lock_reason()
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
    if rest[:1] == ["api"] and any(MERGE_API.search(a) for a in rest[1:]):
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


# ----------------------------------------------------------------------------- frozen spec at commit time
#
# The Edit/Write and shell checks cannot see every way to write a file (a script file, a patch, code fed to an
# interpreter on stdin). A change to the specification counts only once it is committed, so while the spec is frozen
# every git command that creates commits is checked for what it would commit.

COMMIT_CREATING = {"commit", "merge", "cherry-pick", "am"}
COMMIT_PLUMBING = {"commit-tree", "fast-import"}
MERGE_VALUE_OPTIONS = {"-m", "-F", "-s", "-X", "--message", "--file", "--strategy", "--strategy-option",
                       "--into-name", "--cleanup"}
CHERRY_PICK_VALUE_OPTIONS = {"-m", "--mainline", "-X", "--strategy", "--strategy-option", "--cleanup"}
AM_VALUE_OPTIONS = {"-C", "--whitespace", "--resolvemsg", "--quoted-cr", "--empty", "--patch-format"}
AM_PATH_REMAPPING = ("-p", "--directory", "--include", "--exclude")
SEQUENCER_EXITS = ("--abort", "--quit", "--skip", "--show-current-patch")
OTHER_LOCATION = re.compile(r"\bGIT_(?:DIR|WORK_TREE|INDEX_FILE|OBJECT_DIRECTORY|COMMON_DIR)=|--git-dir\b|--work-tree\b"
                            r"|core\.worktree|core\.bare")
PATCH_PATH = re.compile(r'^(?:diff --git "?a/(\S+?)"? "?b/(\S+?)"?|(?:---|\+\+\+) "?[ab]/(\S+?)"?(?:\t.*)?|'
                        r'(?:rename|copy) (?:from|to) "?(\S+?)"?)$', re.M)


def frozen_spec_commits(command, ctx, cwd):
    command = strip_heredocs(command)        # a heredoc body (a commit message) is text, not commands
    unquoted = QUOTED.sub("''", command)
    for segment in segments(command):
        words = words_of(segment)
        cwd = follow_cd(words, cwd, ctx.project)
        if len(words) > 1 and os.path.basename(words[0]) == "git":
            reason = frozen_spec_commit(words[1:], ctx, cwd, unquoted)
            if reason:
                return reason
    return None


def frozen_spec_commit(args, ctx, cwd, command):
    sub = git_subcommand(args)
    if sub not in COMMIT_CREATING and sub not in COMMIT_PLUMBING:
        return None
    rest = args[args.index(sub) + 1:]
    repo = repository_of(args, cwd)
    if repo is None:
        return None
    spec = ctx if os.path.realpath(repo) == os.path.realpath(ctx.project) else Context(repo)
    if sub in COMMIT_PLUMBING:
        return plumbing_reason(sub) if ctx.frozen or spec.frozen else None
    if any(a in SEQUENCER_EXITS for a in rest):
        return None
    if OTHER_LOCATION.search(command):
        return location_reason(sub) if spec.frozen else None
    changed = spec_changes(sub, rest, spec, cwd)
    if not changed or not spec.frozen:
        return None
    return frozen_commit_reason(sub, changed)


def repository_of(args, cwd):
    """The working tree a git command acts on: `git -C dir ...` (several compose) relative to the shell's directory."""
    directory = cwd
    i = 0
    while i < len(args) and args[i].startswith("-"):
        if args[i] == "-C" and i + 1 < len(args):
            target = os.path.expanduser(args[i + 1])
            directory = os.path.realpath(target if os.path.isabs(target) else os.path.join(directory, target))
        i += 2 if args[i] in ("-C", "-c") else 1
    return git(directory, "rev-parse", "--show-toplevel") or None


def spec_changes(sub, rest, spec, cwd):
    if sub == "commit" or "--continue" in rest or "--resolved" in rest:
        return pending_spec_changes(spec)
    if sub == "merge":
        return merged_spec_changes(rest, spec)
    if sub == "cherry-pick":
        return picked_spec_changes(rest, spec)
    return patched_spec_changes(rest, spec, cwd)


def pending_spec_changes(spec):
    """Spec files that differ from HEAD in the index or the working tree; while a merge of the base branch is being
    concluded, a file that is exactly the base's version is the base's own change, not the agent's."""
    dirs = spec.spec_dirs
    paths = set(lines(git(spec.project, "diff", "--name-only", "HEAD", "--", *dirs)))
    paths |= set(lines(git(spec.project, "diff", "--cached", "--name-only", "HEAD", "--", *dirs)))
    merge_head = git(spec.project, "rev-parse", "-q", "--verify", "MERGE_HEAD")
    if paths and merge_head and from_base(spec, merge_head):
        paths = {p for p in paths if not same_as(spec, merge_head, p)}
    return sorted(paths)


def merged_spec_changes(rest, spec):
    changed = set()
    for ref in positional(rest, MERGE_VALUE_OPTIONS):
        rev = git(spec.project, "rev-parse", "-q", "--verify", ref + "^{commit}")
        if not rev or from_base(spec, rev):
            continue
        fork = git(spec.project, "merge-base", "HEAD", rev) or "HEAD"
        changed |= set(lines(git(spec.project, "diff", "--name-only", fork, rev, "--", *spec.spec_dirs)))
    return sorted(changed)


def picked_spec_changes(rest, spec):
    if "--stdin" in rest:
        return ["(revisions read from standard input)"]
    changed = set()
    for arg in positional(rest, CHERRY_PICK_VALUE_OPTIONS):
        revs = lines(git(spec.project, "rev-list", arg)) if ".." in arg else \
            [git(spec.project, "rev-parse", "-q", "--verify", arg + "^{commit}")]
        for rev in filter(None, revs):
            changed |= set(lines(git(spec.project, "diff-tree", "--no-commit-id", "--name-only", "-r", "-m", "--root",
                                     rev, "--", *spec.spec_dirs)))
    return sorted(changed)


def patched_spec_changes(rest, spec, cwd):
    """`git am`: the patches are read before they are applied; a patch the guard cannot read counts as a change."""
    if any(a.startswith(AM_PATH_REMAPPING) for a in rest):
        return ["(patch paths remapped)"]
    files, rest, from_stdin = redirected_inputs(rest)
    files += positional(rest, AM_VALUE_OPTIONS)
    if from_stdin or not files:
        return ["(patch read from standard input)"]
    changed = set()
    for name in files:
        path = os.path.expanduser(name)
        path = path if os.path.isabs(path) else os.path.join(cwd, path)
        if os.path.isdir(path):
            return [f"(patches in {name})"]
        try:
            with open(path, encoding="utf-8", errors="replace") as f:
                text = f.read()
        except OSError:
            continue                      # git am fails on a missing file by itself
        for match in PATCH_PATH.finditer(text):
            changed |= {p for p in match.groups() if p and is_under(p, spec.features_dirs)}
    return sorted(changed)


def redirected_inputs(rest):
    """`git am < file` reads the file (it can be inspected); a heredoc or a pipe cannot."""
    files, others, from_stdin, i = [], [], False, 0
    while i < len(rest):
        token = rest[i]
        if token.startswith("<<"):
            from_stdin = True
            i += 2 if token in ("<<", "<<-") else 1
            continue
        if token == "<" and i + 1 < len(rest):
            files.append(rest[i + 1])
            i += 2
            continue
        if token.startswith("<") and len(token) > 1:
            files.append(token[1:])
        else:
            others.append(token)
        i += 1
    return files, others, from_stdin


def positional(rest, value_options):
    found, skip = [], False
    for a in rest:
        if skip:
            skip = False
        elif a == "--":
            continue
        elif a in value_options:
            skip = True
        elif not a.startswith("-"):
            found.append(a)
    return found


def from_base(spec, rev):
    """The commit is already on the base branch (merging the base brings the other tasks' approved specs)."""
    if git_code(spec.project, "merge-base", "--is-ancestor", rev, spec.base_ref) == 0:
        return True
    return spec._refresh_base() and git_code(spec.project, "merge-base", "--is-ancestor", rev, spec.base_ref) == 0


def same_as(spec, rev, path):
    return (git_code(spec.project, "diff", "--quiet", rev, "--", path) == 0
            and git_code(spec.project, "diff", "--cached", "--quiet", rev, "--", path) == 0)


def lines(text):
    return [line for line in text.splitlines() if line.strip()]


def frozen_commit_reason(sub, changed):
    shown = ", ".join(changed[:3]) + (f" and {len(changed) - 3} more" if len(changed) > 3 else "")
    return (f"The specification is frozen (committed on this branch, approved by the human), and this git {sub} would "
            f"commit a change to it: {shown}. However the files were changed, only the human changes an approved spec "
            f"(on GitHub, or in a session with GAUNTLET_GUARD=off). Leave the feature files as committed, stop and "
            f"describe the problem to the human.")


def plumbing_reason(sub):
    return (f"git {sub} builds commits the guard cannot inspect, and the specification is frozen. Use git commit; "
            f"plumbing commits are reserved for the human.")


def location_reason(sub):
    return (f"This git {sub} uses another git directory, work tree or index, which the guard cannot inspect, and the "
            f"specification is frozen. Run it in the task's working tree as it is.")


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
    words = split_words(segment)
    while words and (re.match(r"^\w+=", words[0]) or words[0] in WRAPPERS or words[0] in SHELL_KEYWORDS
                     or words[0] in ("xargs", "function")):
        wrapper = words.pop(0)
        if wrapper == "function" and words:
            words.pop(0)       # `function name { rm x; }`: the body runs rm
        if wrapper in WRAPPERS or wrapper == "xargs":
            words = without_wrapper_options(wrapper, words)
    return words


def without_wrapper_options(wrapper, words):
    """Drop the options of a wrapper (and their values) and the duration of timeout; `env -S "rm x"` runs rm x."""
    takes_value = WRAPPER_VALUE_OPTIONS.get(wrapper, set())
    while words and words[0].startswith("-") and words[0] != "-":
        option = words.pop(0)
        if option == "--":
            break
        if option in takes_value and words:
            value = words.pop(0)
            if option in ("-S", "--split-string") and wrapper == "env":
                words = split_words(value) + words
    if wrapper == "timeout" and words:
        words.pop(0)           # the duration
    return words


def split_words(text):
    try:
        return shlex.split(text, posix=True)
    except ValueError:
        return text.split()


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
    """The command names this path as a whole segment ('gauntlet' matches 'gauntlet/x', not 'gauntletFast');
    a name inside a Gradle output directory ('build/gauntlet/x.log') is not the protected path."""
    return re.search(r"(?<![\w.-])(?<!build/)(?<!\.gradle/)" + re.escape(path) + r"(?![\w-])", command) is not None


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

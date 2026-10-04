#!/usr/bin/env python3
"""Stop / SubagentStop gate for the gauntlet (Claude Code hook).

An agent may not finish while the gauntlet is red.

  stop_gate.py --record-start                              # SessionStart: remember the working tree
  stop_gate.py --task gauntletFast [--max-attempts 3]      # Stop / SubagentStop

The gate runs only when the content of the working tree differs from what it was when the session
started (the developer's pre-existing uncommitted work does not count, and committing it changes
nothing) and that exact content has not been verified before - so questions and read-only turns cost
nothing, and a red result is not re-run until something changes. A turn that changed nothing but the
feature files never runs the gate: the specification waits for the human, not for the gauntlet. While
uncommitted feature changes wait for the human's approval in the chat, the acceptance tests are left
out of the run (the agent must be able to stop and ask), but every other check still runs. On red it
exits 2 and feeds the failing lines back to the agent, which keeps working. After --max-attempts
consecutive red stops it lets the agent stop and warns instead, so a session can never loop forever.

Humans can switch it off for a session:  GAUNTLET_STOP_HOOK=off (environment variable at start-up)
Extra Gradle arguments (e.g. --offline):  GAUNTLET_GRADLE_ARGS="--offline"
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time

BUILD_FILES = ["src", "gradle", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
               "gradle.properties"]
PROPERTIES = "gauntlet/config/gauntlet.properties"
DEFAULT_FEATURES = "src/acceptanceTest/resources/features"
SPEC_DEPENDENT_TASKS = ["acceptanceTest", "acceptanceCheck"]
# "module" in gauntlet.properties: the Gradle build directories inside a monorepo, comma-separated
# ("" = one build at the repository root). Gradle runs in each changed module; every path below is
# relative to the repository root.
MODULES = []
WATCHED = list(BUILD_FILES) + ["gauntlet"]
REPORT_DIRS = ["build/test-results", "build/reports"]   # newer files here = the agent ran Gradle itself
INTERESTING = re.compile(
    r"^(CRAP FAIL|DUPLICATE|DRY gate: FAILED|INTEGRITY|"
    r"MUTANT (SURVIVED|ERROR)|SURVIVED|NO_COVERAGE|RUN_ERROR|MEMORY_ERROR|NON_VIABLE|mutationGate: |"
    r"\s+\S+#\S+ \w+Mutator\w*$|ACCEPTANCE SKIPPED|WARNING .*mutation sites|"
    r"\[ERROR\]|\[ant:checkstyle\]|> Task .*FAILED|.*\bFAILED$|Execution failed for task|"
    r".*[A-Za-z](Exception|Error)(:|\s|$)|.*expected:?\s|Caused by|Architecture Violation|Rule '|"
    r"\s+at [a-z][\w.$]*\(\w+Test\.java:\d+\)|.*tests? completed, \d+ failed|gauntlet: )")
NOISE = re.compile(r"^(Picked up (JAVA_TOOL_OPTIONS|_JAVA_OPTIONS|JDK_JAVA_OPTIONS)|"
                   r"> Task .*(UP-TO-DATE|NO-SOURCE|SKIPPED)$|\s*$)")
CONTINUATION_WINDOW_S = 20 * 60


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--record-start", action="store_true")
    ap.add_argument("--task", default="gauntletFast")
    ap.add_argument("--max-attempts", type=int, default=3)
    ap.add_argument("--timeout", type=int, default=1500)
    opts = ap.parse_args()
    try:
        event = json.load(sys.stdin)
    except Exception:
        event = {}
    project = os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or os.getcwd()
    os.chdir(project)
    configure_module()
    state = state_dir()
    if state is None:
        return 0
    session = safe(event.get("session_id", "unknown"))
    if opts.record_start:
        start_file = os.path.join(state, session + ".start")
        if not os.path.exists(start_file):
            write(start_file, json.dumps({"head": git("rev-parse", "HEAD"), "tree": tree_fingerprint(),
                                          "dirty": dirty_files()}))
        return 0
    if os.environ.get("GAUNTLET_STOP_HOOK", "").lower() == "off":
        return 0
    return gate(opts, event, state, session)


def gate(opts, event, state, session):
    start_file = os.path.join(state, session + ".start")
    try:
        start = json.loads(read(start_file))
    except ValueError:
        # SessionStart did not record a baseline: judge by uncommitted changes against HEAD instead
        start = {"head": git("rev-parse", "HEAD"), "tree": None}
        write(start_file, json.dumps(start))
    tree = tree_fingerprint()
    if tree == start.get("tree"):
        return 0  # nothing changed in this session
    changed = changed_paths(start.get("head", ""))
    # the developer's own uncommitted work from before the session is not the agent's change (unless touched since)
    baseline = start.get("dirty") or {}
    changed = {p for p in changed if baseline.get(p) != file_hash(p)}
    if not changed:
        return 0
    features = features_dirs()
    if all(any(("/" + p).startswith("/" + f + "/") for f in features) for p in changed):
        return 0  # only the spec changed: it waits for the human's approval, not for the gauntlet
    affected = [m for m in MODULES if any(p.startswith(m + "/") for p in changed)] if MODULES else ["."]
    if not affected:
        return 0  # nothing inside a guarded build changed
    pending = specs_pending_approval(features)
    excluded = SPEC_DEPENDENT_TASKS if pending else []
    key = session + "-" + safe(event.get("agent_id") or "main")
    # The last-stop mark (code tree without the spec | pending/approved | ok/red) remembers the state in
    # which this agent was last allowed to stop. Same code since then = this turn changed only the spec
    # (or nothing): the question to the human must not wait for Gradle. Exceptions: the last stop was green
    # with the acceptance tests left out and the spec has been committed since (pending -> approved) - check
    # now; the last stop released a RED result - remind the agent, and re-run when it ran Gradle itself since
    # (newer test reports) unless a spec draft is waiting for the human, whose question goes first.
    code_tree = tree_fingerprint(exclude=features)
    state_tag = "pending" if pending else "approved"
    stop_mark = os.path.join(state, key + ".last-stop")
    last = read(stop_mark).split("|")
    if len(last) == 3 and last[0] == code_tree and not (last[1] == "pending" and not pending):
        if last[2] == "ok":
            write(stop_mark, f"{code_tree}|{state_tag}|ok")
            return 0
        if pending or not newer_than(mtime(stop_mark), REPORT_DIRS):
            write(stop_mark, f"{code_tree}|{state_tag}|red")
            print(json.dumps({"systemMessage":
                              f"[gauntlet] reminder: {opts.task} was still RED when you were last allowed to stop and the "
                              f"code has not changed since. The work is NOT done - say so in your report."}))
            return 0
    fingerprint = hashlib.sha256((opts.task + ("|pending" if pending else "") + "|".join(affected) + tree).encode()).hexdigest()
    green_file = os.path.join(state, "last-green-" + safe(opts.task))
    if read(green_file) == fingerprint:
        write(stop_mark, f"{code_tree}|{state_tag}|ok")
        return 0
    attempts_file = os.path.join(state, key + ".attempts")
    red_file = os.path.join(state, "last-red-" + safe(opts.task))
    logs = [log_file(m, opts.task) for m in affected]
    cached = read(red_file) == fingerprint and all(os.path.exists(l) for l in logs) \
        and not newer_than(min(mtime(l) for l in logs), REPORT_DIRS)
    if cached:
        code, output = 1, "\n".join(read(l) for l in logs)   # nothing changed since the last red run: no re-run
    else:
        code, output = run_all(affected, opts.task, excluded, opts.timeout)
    log_path = ", ".join(logs)
    note = (" Acceptance tests were left out: the changed feature files wait for the human's approval "
            "in the chat (commit them only after the human said yes).") if pending else ""
    if code == 0:
        write(green_file, fingerprint)
        write(stop_mark, f"{code_tree}|{state_tag}|ok")
        remove(attempts_file)
        remove(red_file)
        if pending:
            print(json.dumps({"systemMessage": f"[gauntlet] {opts.task} is green.{note}"}))
        return 0
    write(red_file, fingerprint)
    attempts = next_attempt(attempts_file, event)
    if attempts > opts.max_attempts:
        remove(attempts_file)
        write(stop_mark, f"{code_tree}|{state_tag}|red")
        print(json.dumps({"systemMessage":
                          f"[gauntlet] {opts.task} is still RED after {opts.max_attempts} attempts - stopping anyway. "
                          f"The work is NOT done: say so in your report, with the causes; see {log_path}.{note}"}))
        return 0
    sys.stderr.write(
        f"[gauntlet] ./gradlew {opts.task} is RED (attempt {attempts}/{opts.max_attempts}). You cannot finish "
        f"yet: fix the causes below. Do not weaken tests, specs or checks.{note}\n\n{summarize(output)}\n\n"
        f"Full log: {log_path}\n")
    return 2


def uncommitted_paths():
    paths = set()
    status = git_bytes("status", "--porcelain", "--untracked-files=all", "--", *existing(WATCHED)).decode("utf-8", "replace")
    for line in status.splitlines():  # "XY path", relative to the repository root - do not strip: X may be a space
        paths.add(line[3:].strip().split(" -> ")[-1].strip('"'))
    return {p for p in paths if p}


def dirty_files():
    """Uncommitted files at session start with their content hash."""
    return {p: file_hash(p) for p in uncommitted_paths()}


def file_hash(path):
    h = hashlib.sha256()
    try:
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 16), b""):
                h.update(chunk)
    except OSError:
        return "<missing>"
    return h.hexdigest()


def changed_paths(start_head):
    """Paths changed in this session: uncommitted changes plus commits made since the session started."""
    paths = uncommitted_paths()
    if start_head and git("rev-parse", "HEAD") != start_head:
        paths.update(git("diff", "--name-only", start_head, "HEAD", "--", *existing(WATCHED)).splitlines())
    return {p for p in paths if p}


def next_attempt(attempts_file, event):
    previous, stamp = 0, 0.0
    raw = read(attempts_file)
    if raw:
        try:
            previous, stamp = int(raw.split()[0]), float(raw.split()[1])
        except (ValueError, IndexError):
            previous = 0
    # Stop: stop_hook_active is set by Claude Code after a blocked stop. SubagentStop: the same field only says
    # whether the subagent has Stop hooks of its own (hooks docs), so its retries are told apart by time.
    if event.get("hook_event_name") == "Stop" or ("stop_hook_active" in event
                                                  and event.get("hook_event_name") != "SubagentStop"):
        continuing = bool(event.get("stop_hook_active"))
    else:
        continuing = time.time() - stamp < CONTINUATION_WINDOW_S
    attempts = previous + 1 if continuing else 1
    write(attempts_file, f"{attempts} {time.time()}")
    return attempts


def log_file(module, task):
    return os.path.join(module, "build", "gauntlet", f"{task}-hook.log")


def run_all(modules, task, excluded, timeout):
    """Runs the task in every affected module; red if any module is red."""
    worst, outputs = 0, []
    for module in modules:
        code, _, output = run_gradle(task, excluded, timeout, module)
        worst = max(worst, code)
        outputs.append((f"=== {module} ===\n" if module != "." else "") + output)
    return worst, "\n".join(outputs)


def run_gradle(task, excluded, timeout, module="."):
    gradle = "./gradlew" if os.access(os.path.join(module, "gradlew"), os.X_OK) else "gradle"
    cmd = [gradle, task, "--continue", "--console=plain"]
    for name in excluded:
        cmd += ["-x", name]
    cmd += os.environ.get("GAUNTLET_GRADLE_ARGS", "").split()
    os.makedirs(os.path.dirname(log_file(module, task)), exist_ok=True)
    log_path = log_file(module, task)
    try:
        proc = subprocess.run(cmd, cwd=module, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                              timeout=timeout)
        code, output = proc.returncode, proc.stdout
    except subprocess.TimeoutExpired as e:
        code = 124
        output = (e.stdout or "") if isinstance(e.stdout, str) else ""
        output += f"\nTIMEOUT: {' '.join(cmd)} did not finish within {timeout}s"
    except FileNotFoundError:
        code, output = 127, f"Cannot run {gradle}: not found"
    write(log_path, output)
    return code, log_path, output


def summarize(output, limit=12000):
    lines = [l for l in output.splitlines() if not NOISE.match(l)]
    picked, seen = [], set()
    for line in lines:
        if INTERESTING.match(line) and line not in seen:
            seen.add(line)
            picked.append(line)
    tail = [l for l in lines[-30:] if l not in seen]
    text = "\n".join(picked[:150]) + "\n\n--- end of the Gradle output ---\n" + "\n".join(tail)
    return text if len(text) <= limit else text[:limit] + "\n[...truncated]"


def tree_fingerprint(exclude=()):
    """Content of every tracked or untracked (not ignored) file under the watched paths - not HEAD."""
    h = hashlib.sha256()
    pathspec = existing(WATCHED) + [f":(exclude){e}" for e in exclude]
    files = git_bytes("ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", *pathspec)
    for path in sorted(set(p for p in files.decode("utf-8", "replace").split("\0") if p)):
        h.update(path.encode() + b"\0")
        try:
            with open(path, "rb") as f:
                for chunk in iter(lambda: f.read(1 << 16), b""):
                    h.update(chunk)
        except OSError:
            h.update(b"<missing>")
    return h.hexdigest()


def properties():
    props = {}
    for line in read(PROPERTIES).splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def configure_module():
    """Point every path at the modules' build directories when the build is not at the repository root."""
    global MODULES, WATCHED, REPORT_DIRS
    MODULES = [m.strip().strip("/") for m in properties().get("module", "").split(",") if m.strip()]
    if MODULES:
        WATCHED = [os.path.join(m, p) for m in MODULES for p in BUILD_FILES] + ["gauntlet"]
        REPORT_DIRS = [os.path.join(m, p) for m in MODULES for p in REPORT_DIRS]


def features_dirs():
    rel = properties().get("acceptance.features", DEFAULT_FEATURES).strip("/")
    return [os.path.join(m, rel) for m in MODULES] if MODULES else [rel]


def specs_pending_approval(features):
    """Feature files that are changed but not committed wait for the human's 'yes' in the chat.
    Once committed on the branch they are the approved specification (and the guard freezes them)."""
    dirs = [f for f in features if os.path.isdir(f)]
    if not dirs:
        return False
    status = git_bytes("status", "--porcelain", "--untracked-files=all", "--", *dirs)
    return bool(status.strip())


def mtime(path):
    try:
        return os.path.getmtime(path)
    except OSError:
        return 0.0


def newer_than(stamp, dirs):
    """Any file under dirs modified after stamp."""
    for d in dirs:
        for root, _, files in os.walk(d):
            for name in files:
                if mtime(os.path.join(root, name)) > stamp:
                    return True
    return False


def state_dir():
    path = git("rev-parse", "--git-path", "gauntlet-hook")
    if not path:
        return None
    os.makedirs(path, exist_ok=True)
    return path


def git(*args):
    return git_bytes(*args).decode("utf-8", "replace").strip()


def git_bytes(*args):
    try:
        return subprocess.run(["git", "-c", "core.quotePath=false", *args], stdout=subprocess.PIPE,
                              stderr=subprocess.DEVNULL).stdout
    except OSError:
        return b""


def existing(paths):
    return [p for p in paths if os.path.exists(p)] or ["."]


def safe(value):
    return re.sub(r"[^A-Za-z0-9_.-]", "_", str(value))[:80]


def read(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read().strip()
    except OSError:
        return ""


def write(path, text):
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def remove(path):
    try:
        os.remove(path)
    except OSError:
        pass


if __name__ == "__main__":
    sys.exit(main())

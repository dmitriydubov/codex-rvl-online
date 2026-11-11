#!/usr/bin/env python3
"""Self-test for the gauntlet hooks. Builds a throwaway git repository with a fake base branch and
runs guard.py / stop_gate.py against it the way Claude Code does (JSON on stdin, environment set).

    python3 gauntlet/hooks/selftest.py          # from any directory; needs git and python3

Gradle is stubbed with a fake ./gradlew that exits with the code found in a marker file, so the
Stop-gate logic is exercised without a JVM.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time

HOOKS = os.path.dirname(os.path.abspath(__file__))
KIT = os.path.dirname(HOOKS)
FEATURES_REL = "src/acceptanceTest/resources/features"
MODULES = []          # Gradle builds inside the repository: [] = one build at the root, ["core", "adapter"] = monorepo
M = ""                # prefix of the module the shared tests work in ("" or "core/")
FEATURES = FEATURES_REL
FAKE_GRADLEW = ("#!/bin/sh\ncode=$(cat build/gauntlet/fake-exit 2>/dev/null || echo 0)\n"
                "sleep \"$(cat build/gauntlet/fake-sleep 2>/dev/null || echo 0)\"\n"
                "echo \"fake gradle $*\"\n[ \"$code\" = 0 ] || echo 'CRAP FAIL com.example.X#y'\nexit $code\n")
failures = []
passed = 0


def sh(repo, *cmd, **kw):
    return subprocess.run(cmd, cwd=repo, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, **kw)


def git(repo, *args):
    r = sh(repo, "git", *args)
    assert r.returncode == 0, f"git {' '.join(args)}: {r.stdout}"
    return r.stdout.strip()


def make_repo():
    root = tempfile.mkdtemp(prefix="gauntlet-selftest-")
    repo = os.path.join(root, "service")
    bare = os.path.join(root, "origin.git")
    os.makedirs(repo)
    git(repo, "init", "-q", "-b", "main")
    git(repo, "config", "user.email", "t@example.com")
    git(repo, "config", "user.name", "t")
    shutil.copytree(KIT, os.path.join(repo, "gauntlet"), ignore=shutil.ignore_patterns("__pycache__"))
    # the layout under test decides `module`, not the installed kit's value (a monorepo install sets it)
    props = os.path.join(repo, "gauntlet/config/gauntlet.properties")
    with open(props) as f:
        lines = [line for line in f.read().splitlines() if not line.strip().startswith("module=")]
    with open(props, "w") as f:
        f.write("\n".join(lines) + f"\nmodule={','.join(MODULES)}\n")
    for prefix in [m + "/" for m in MODULES] or [""]:
        write(repo, prefix + "build.gradle.kts", 'plugins { java }\napply(from = "../gauntlet/gauntlet.gradle")\n')
        write(repo, prefix + "src/main/java/com/example/service/domain/Order.java",
              "package com.example.service.domain;\npublic class Order { public int discount(int p) { return p / 10; } }\n")
        write(repo, prefix + "src/test/java/com/example/service/domain/OrderTest.java",
              "package com.example.service.domain;\nclass OrderTest { }\n")
        write(repo, prefix + FEATURES_REL + "/.gitkeep", "")
        write(repo, prefix + "gradlew", FAKE_GRADLEW)
        os.chmod(os.path.join(repo, prefix + "gradlew"), 0o755)
    git(repo, "add", "-A")
    git(repo, "commit", "-q", "-m", "base")
    subprocess.run(["git", "init", "-q", "--bare", bare], check=True)
    git(repo, "remote", "add", "origin", bare)
    git(repo, "push", "-q", "origin", "main")
    git(repo, "checkout", "-q", "-b", "claude/feature-x")
    return repo


def write(repo, rel, text):
    path = os.path.join(repo, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)


def run_hook(repo, script, event, env=None, args=()):
    e = dict(os.environ, CLAUDE_PROJECT_DIR=repo)
    e.pop("GAUNTLET_GUARD", None)
    e.pop("GAUNTLET_STOP_HOOK", None)
    e.update(env or {})
    r = subprocess.run([sys.executable, os.path.join(repo, "gauntlet", "hooks", script), *args], cwd=repo,
                       input=json.dumps(event), stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=e)
    return r


def guard(repo, agent, tool, cwd=None, **params):
    event = {"session_id": "s1", "cwd": cwd or repo, "tool_name": tool, "tool_input": params}
    if agent != "main":
        event["agent_type"] = agent
    r = run_hook(repo, "guard.py", event)
    if r.returncode != 0:
        return "CRASH " + r.stderr
    if not r.stdout.strip():
        return None
    return json.loads(r.stdout)["hookSpecificOutput"]["permissionDecisionReason"]


def check(name, condition, detail=""):
    global passed
    if condition:
        passed += 1
    else:
        failures.append(f"{name}  {detail}")


def expect_deny(repo, name, agent, tool, **params):
    reason = guard(repo, agent, tool, **params)
    check(name, reason is not None and not reason.startswith("CRASH"), f"expected deny, got {reason!r}")


def expect_allow(repo, name, agent, tool, **params):
    reason = guard(repo, agent, tool, **params)
    check(name, reason is None, f"expected allow, got {reason!r}")


def guard_tests(repo):
    feature = FEATURES + "/order.feature"
    # --- human-owned files
    for path in ("gauntlet/config/gauntlet.properties", "gauntlet/gauntlet.gradle", "gauntlet/hooks/guard.py",
                 ".claude/settings.json", ".claude/agents/specifier.md", "CLAUDE.md", ".github/workflows/gauntlet.yml"):
        expect_deny(repo, f"edit {path}", "main", "Edit", file_path=path, old_string="a", new_string="b")
    expect_deny(repo, "write absolute gauntlet path", "main", "Write",
                file_path=os.path.join(repo, "gauntlet/config/crap-allowlist.txt"), content="x")
    expect_allow(repo, "edit main code", "main", "Edit", file_path=M + "src/main/java/com/example/service/domain/Order.java",
                 old_string="a", new_string="b")
    expect_allow(repo, "edit build.gradle.kts", "main", "Edit", file_path=M + "build.gradle.kts", old_string="a", new_string="b")
    expect_allow(repo, "write step definitions", "main", "Write",
                 file_path=M + "src/acceptanceTest/java/com/example/service/acceptance/Steps.java", content="x")
    # --- shell writes to protected paths
    expect_deny(repo, "sed -i on gauntlet", "main", "Bash", command="sed -i 's/8/80/' gauntlet/config/gauntlet.properties")
    expect_deny(repo, "redirect into gauntlet", "main", "Bash", command="echo x > gauntlet/config/crap-allowlist.txt")
    expect_deny(repo, "cp over .claude", "main", "Bash", command="cp /tmp/s.json .claude/settings.json")
    expect_deny(repo, "python -c writing CLAUDE.md", "main", "Bash", command="python3 -c \"open('CLAUDE.md','w').write('')\"")
    expect_deny(repo, "git checkout gauntlet", "main", "Bash", command="git checkout -- gauntlet/config/checkstyle.xml")
    expect_deny(repo, "rm gauntlet dir", "main", "Bash", command="rm -rf gauntlet")
    expect_deny(repo, "hook switch", "main", "Bash", command="GAUNTLET_GUARD=off ./gradlew gauntlet")
    expect_deny(repo, "export hook switch", "main", "Bash", command="export GAUNTLET_STOP_HOOK=off; ./gradlew test")
    # --- no false positives
    expect_allow(repo, "gradlew gauntletFast", "main", "Bash", command="./gradlew gauntletFast --continue")
    expect_allow(repo, "gradlew gauntlet", "main", "Bash", command="./gradlew gauntlet --continue -Pgauntlet.baseRef=origin/main")
    expect_allow(repo, "cat gauntlet file", "main", "Bash", command="cat gauntlet/config/gauntlet.properties")
    expect_allow(repo, "git add -A", "main", "Bash", command="git add -A && git commit -m 'feat: discount'")
    expect_allow(repo, "git add gauntlet path", "main", "Bash", command="git add gauntlet/ .claude/ CLAUDE.md")
    expect_allow(repo, "commit mentioning gauntlet", "main", "Bash", command='git commit -m "chore: install the gauntlet"')
    expect_allow(repo, "git push own branch", "main", "Bash", command="git push -u origin HEAD")
    expect_allow(repo, "git push named branch", "main", "Bash", command="git push origin claude/feature-x")
    expect_allow(repo, "rm build/reports", "main", "Bash", command=f"rm -rf {M}build/reports")
    expect_allow(repo, "rm build/gauntlet (hook log dir)", "main", "Bash", command=f"rm -rf {M}build/gauntlet")
    expect_allow(repo, "tee into build/gauntlet", "main", "Bash", command=f"./gradlew gauntletFast 2>&1 | tee {M}build/gauntlet/run.log")
    expect_allow(repo, "cp hook log out", "main", "Bash", command=f"cp {M}build/gauntlet/gauntletFast-hook.log /tmp/")
    expect_allow(repo, "stash with gauntlet in message", "main", "Bash", command='git stash push -m "before gauntlet run"')
    expect_allow(repo, "mkdir build dir", "main", "Bash", command=f"mkdir -p {M}build/tmp && echo x > {M}build/tmp/a.txt")
    expect_allow(repo, "redirect 2>&1", "main", "Bash", command="./gradlew test 2>&1 | tail -20")
    expect_allow(repo, "redirect to /dev/null", "main", "Bash", command="./gradlew test > /dev/null 2>&1")
    expect_deny(repo, "quoted redirect into gauntlet", "main", "Bash", command='echo x > "gauntlet/config/crap-allowlist.txt"')
    expect_deny(repo, "heredoc into CLAUDE.md", "main", "Bash", command="cat > 'CLAUDE.md' <<EOF\nx\nEOF")
    expect_deny(repo, "2> into .claude", "main", "Bash", command="ls 2> .claude/settings.json")
    expect_deny(repo, "&> into .claude", "main", "Bash", command="ls &> .claude/settings.json")
    expect_deny(repo, "1> into gauntlet", "main", "Bash", command="ls 1> gauntlet/config/x")
    expect_deny(repo, "rm -rf star", "main", "Bash", command="rm -rf *")
    expect_deny(repo, "rm -rf dot", "main", "Bash", command="rm -rf .")
    expect_deny(repo, "rm glob under gauntlet", "main", "Bash", command="rm gauntlet/config/*.txt")
    expect_deny(repo, "cd into gauntlet then write", "main", "Bash", command="cd gauntlet/config && cp /tmp/x crap-allowlist.txt")
    expect_deny(repo, "git -C gauntlet checkout", "main", "Bash", command="git -C gauntlet checkout -- config/checkstyle.xml")
    expect_deny(repo, "write ~/.claude settings", "main", "Write", file_path=os.path.expanduser("~/.claude/settings.json"), content="{}")
    expect_deny(repo, "redirect into ~/.claude", "main", "Bash", command="echo '{\"disableAllHooks\": true}' > ~/.claude/settings.json")
    expect_deny(repo, "checkout -B to base", "main", "Bash", command="git checkout -B claude/feature-x origin/main")
    expect_deny(repo, "switch -C to base", "main", "Bash", command="git switch -C claude/feature-x origin/main")
    expect_deny(repo, "branch -f", "main", "Bash", command="git branch -f claude/feature-x origin/main")
    expect_deny(repo, "branch -D", "main", "Bash", command="git branch -D claude/feature-x")
    expect_allow(repo, "checkout -b new branch", "main", "Bash", command="git checkout -b claude/feature-z")
    expect_allow(repo, "switch existing", "main", "Bash", command="git switch main")
    expect_deny(repo, "gh -R pr merge", "main", "Bash", command="gh -R o/r pr merge 12 --squash")
    expect_deny(repo, "gh --repo pr merge", "main", "Bash", command="gh --repo o/r pr merge 12")
    # --- PR #25, decision 5: `gauntlet` as a Gradle task or under build/ is not the kit
    expect_allow(repo, "rm build/gauntlet log with $?", "main", "Bash", command=f"rm -f {M}build/gauntlet/x.log; echo $?")
    expect_allow(repo, "rm missing file then gradlew gauntlet", "main", "Bash",
                 command="rm -f out.log && ./gradlew gauntlet --continue > out.log 2>&1")
    expect_allow(repo, "gradlew gauntlet | tee $VAR", "main", "Bash", command='./gradlew gauntlet --continue 2>&1 | tee "$TMPDIR/run.log"')
    expect_allow(repo, "mkdir $VAR then gradlew gauntlet", "main", "Bash", command='mkdir -p "$SCRATCH" && ./gradlew gauntlet')
    expect_allow(repo, "python -c reads build/gauntlet", "main", "Bash",
                 command=f"python3 -c \"print(open('{M}build/gauntlet/gauntletFast-hook.log').read())\"")
    expect_deny(repo, "rm $VAR with gauntlet assigned", "main", "Bash", command="D=gauntlet; rm -rf $D")
    expect_deny(repo, "rm $VAR next to gradlew gauntlet", "main", "Bash", command="./gradlew gauntlet; X=gauntlet/config; rm -rf $X")
    expect_allow(repo, "python -c next to gradlew gauntlet", "main", "Bash",
                 command="./gradlew gauntlet --continue > out.log 2>&1; python3 -c \"import time;time.sleep(1)\"")
    expect_deny(repo, "python -c writing gauntlet next to gradlew gauntlet", "main", "Bash",
                command="./gradlew gauntlet && python3 -c \"open('gauntlet/config/x','w').write('')\"")
    expect_deny(repo, "echo gauntlet | xargs rm next to gradlew gauntlet", "main", "Bash",
                command="./gradlew gauntlet; echo gauntlet | xargs rm -rf")
    expect_deny(repo, "rm $VAR next to gradlew -p gauntlet", "main", "Bash", command="./gradlew -p gauntlet tasks; rm -rf $X")
    expect_deny(repo, "rm via build/.. back into gauntlet", "main", "Bash", command="rm -rf $P/build/../gauntlet")
    expect_deny(repo, "for ... do rm", "main", "Bash", command='for f in gauntlet/config/*; do rm "$f"; done')
    expect_deny(repo, "then rm", "main", "Bash", command="if true; then rm -rf gauntlet; fi")
    expect_deny(repo, "brace group rm", "main", "Bash", command="{ rm -rf .claude; }")
    expect_deny(repo, "negated rm", "main", "Bash", command="! rm -f CLAUDE.md")
    expect_deny(repo, "do sed -i", "main", "Bash", command="while false; do sed -i 's/8/80/' gauntlet/config/gauntlet.properties; done")
    expect_deny(repo, "then git push --force", "main", "Bash", command="if true; then git push --force; fi")
    # eval, function bodies, bash -c and wrapper options with values run the real command too
    expect_deny(repo, "eval rm", "main", "Bash", command='eval "rm -rf gauntlet"')
    expect_deny(repo, "eval unquoted rm", "main", "Bash", command="eval rm -rf .claude")
    expect_deny(repo, "eval git push --force", "main", "Bash", command="eval 'git push --force'")
    expect_deny(repo, "nested eval", "main", "Bash", command="eval \"eval 'rm -f CLAUDE.md'\"")
    expect_deny(repo, "bash -c git push --force", "main", "Bash", command='bash -c "git push --force"')
    expect_deny(repo, "function body rm", "main", "Bash", command="function f { rm -rf gauntlet; }; f")
    expect_deny(repo, "function () body rm", "main", "Bash", command="function f() { rm -rf .claude; }; f")
    expect_deny(repo, "env -i rm", "main", "Bash", command="env -i rm -rf gauntlet")
    expect_deny(repo, "env -u VAR rm", "main", "Bash", command="env -u HOME rm -rf gauntlet")
    expect_deny(repo, "env -S rm", "main", "Bash", command='env -S "rm -rf gauntlet"')
    expect_deny(repo, "env -i git push --force", "main", "Bash", command="env -i git push --force")
    expect_deny(repo, "nice -n rm", "main", "Bash", command="nice -n 5 rm -rf gauntlet")
    expect_deny(repo, "nice -n git push --force", "main", "Bash", command="nice -n 1 git push --force")
    expect_deny(repo, "timeout -s rm", "main", "Bash", command="timeout -s KILL 5 rm -rf gauntlet")
    expect_deny(repo, "timeout -k rm", "main", "Bash", command="timeout -k 1 5 rm -rf .claude")
    expect_deny(repo, "timeout -s git push --force", "main", "Bash", command="timeout -s 9 10 git push --force")
    expect_deny(repo, "sudo -u rm", "main", "Bash", command="sudo -u root rm -rf gauntlet")
    expect_allow(repo, "eval of a command substitution", "main", "Bash", command='eval "$(ssh-agent -s)"')
    expect_allow(repo, "bash -c gradlew gauntletFast", "main", "Bash", command=f'bash -c "cd {M or "."} && ./gradlew gauntletFast"')
    expect_allow(repo, "timeout gradlew gauntletFast", "main", "Bash", command="timeout 600 ./gradlew gauntletFast --continue")
    expect_allow(repo, "nice -n gradlew gauntlet", "main", "Bash", command="nice -n 10 ./gradlew gauntlet")
    expect_allow(repo, "env -i ls", "main", "Bash", command="env -i PATH=/usr/bin ls")
    # the build-directory lock of gauntlet.gradle: removing it lets a second Gradle run into the same build/
    expect_deny(repo, "rm the build lock", "main", "Bash", command=f"rm -f {M}.gradle/gauntlet-build.lock")
    expect_deny(repo, "rm the build lock via $VAR", "main", "Bash", command="rm -f $D/.gradle/gauntlet-build.lock")
    expect_deny(repo, "rm .gradle with the build lock", "main", "Bash", command=f"rm -rf {M}.gradle/")
    expect_deny(repo, "hardener rm the build lock", "hardener", "Bash", command=f"rm -f {M}.gradle/gauntlet-build.lock")
    expect_allow(repo, "rm .gradle caches", "main", "Bash", command=f"rm -rf {M}.gradle/caches")
    expect_allow(repo, "read the build lock", "main", "Bash", command=f"cat {M}.gradle/gauntlet-build.lock")
    # gh api: only merge endpoints and merge mutations are the human's, not any text with "merge" in it
    expect_allow(repo, "gh api create PR with merge in body", "main", "Bash",
                 command="gh api repos/o/r/pulls -f title=t -f head=b -f base=main -f body='no merge conflicts'")
    expect_allow(repo, "gh api read mergeable state", "main", "Bash", command="gh api repos/o/r/pulls/12 --jq .mergeable")
    expect_deny(repo, "gh api merges endpoint", "main", "Bash", command="gh api repos/o/r/merges -f base=main -f head=b")
    expect_deny(repo, "gh api merge-upstream", "main", "Bash", command="gh api -X POST repos/o/r/merge-upstream -f branch=main")
    expect_deny(repo, "gh api graphql mergePullRequest", "main", "Bash",
                command="gh api graphql -f query='mutation { mergePullRequest(input: {pullRequestId: \"x\"}) { clientMutationId } }'")
    expect_deny(repo, "rm via $PWD", "main", "Bash", command="rm -rf $PWD/gauntlet")
    expect_deny(repo, "rm via $CLAUDE_PROJECT_DIR", "main", "Bash", command='rm -rf "$CLAUDE_PROJECT_DIR/.claude"')
    expect_deny(repo, "xargs rm", "main", "Bash", command="git ls-files gauntlet/config | xargs rm -f")
    expect_deny(repo, "sed -i on substitution", "main", "Bash", command="sed -i 's/8/80/' $(git ls-files gauntlet/config/gauntlet.properties)")
    expect_deny(repo, "pushd then sed", "main", "Bash", command="pushd gauntlet/config && sed -i 's/8/80/' gauntlet.properties")
    expect_allow(repo, "mkdir via $CLAUDE_PROJECT_DIR in tests", "hardener", "Bash", command=f'mkdir -p "$CLAUDE_PROJECT_DIR/{M}src/test/java/com/example/api"')
    expect_allow(repo, "commit message heredoc mentioning install", "main", "Bash",
                 command='git commit -m "$(cat <<\'EOF\'\nchore: install the gauntlet hooks, roles and the CI workflow\n\nrm nothing; patch nothing.\nEOF\n)"')
    expect_allow(repo, "heredoc report into /tmp", "main", "Bash", command="cat > /tmp/report.md <<'EOF'\n- gauntlet/README.md -> see\nrm -rf gauntlet\nEOF")
    expect_deny(repo, "heredoc opener still checked", "main", "Bash", command="cat > gauntlet/config/x <<'EOF'\nx\nEOF")
    expect_allow(repo, "hardener heredoc test with lambda", "hardener", "Bash",
                 command=f"cat > {M}src/test/java/com/example/service/domain/OrderTest.java <<'EOF'\nassertTrue(discount(100) >= 10); list.forEach(x -> check(x));\nEOF")
    expect_allow(repo, "hardener cleans pit history", "hardener", "Bash", command=f"rm -rf {M}build/pitHistory.txt {M}build/reports/pitest")
    expect_allow(repo, "hardener tee into build", "hardener", "Bash", command=f"./gradlew mutationGate --continue 2>&1 | tee {M}build/pit.log")
    expect_allow(repo, "specifier tee into build", "specifier", "Bash", command=f"./gradlew acceptanceTest 2>&1 | tee {M}build/acceptance.log")
    expect_deny(repo, "git revert", "main", "Bash", command="git revert --no-edit HEAD")
    expect_allow(repo, "rm -rf build via var", "main", "Bash", command='rm -rf "$CLAUDE_PROJECT_DIR/build"')
    expect_deny(repo, "gh api graphql automerge", "main", "Bash",
                command="gh api graphql -f query='mutation { enablePullRequestAutoMerge(input: {pullRequestId: \"x\"}) { clientMutationId } }'")
    expect_allow(repo, "gh pr create", "main", "Bash", command='gh pr create --title "feat" --body-file /tmp/report.md')
    expect_allow(repo, "gh pr view", "main", "Bash", command="gh pr view --json url")
    expect_allow(repo, "git reset unstage", "main", "Bash", command=f"git reset HEAD {M}build.gradle.kts")
    expect_allow(repo, "git reset plain", "main", "Bash", command="git reset")
    expect_allow(repo, "git reset path", "main", "Bash", command=f"git reset {M}build.gradle.kts")
    expect_allow(repo, "git commit -am", "main", "Bash", command='git commit -am "msg no verify words"')
    # --- human-only git and gh
    expect_deny(repo, "commit --amend", "main", "Bash", command='git commit --amend -m "x"')
    expect_deny(repo, "commit -n", "main", "Bash", command='git commit -anm "x"')
    expect_deny(repo, "no-verify", "main", "Bash", command="git push --no-verify")
    expect_deny(repo, "rebase", "main", "Bash", command="git rebase -i origin/main")
    expect_deny(repo, "push --force", "main", "Bash", command="git push --force origin claude/feature-x")
    expect_deny(repo, "push -f", "main", "Bash", command="git push -f")
    expect_deny(repo, "push force-with-lease", "main", "Bash", command="git push --force-with-lease")
    expect_deny(repo, "push to main", "main", "Bash", command="git push origin main")
    expect_deny(repo, "push HEAD:main", "main", "Bash", command="git push origin HEAD:main")
    expect_deny(repo, "push +refspec", "main", "Bash", command="git push origin +claude/feature-x")
    expect_deny(repo, "reset --hard revision", "main", "Bash", command="git reset --hard origin/main")
    expect_deny(repo, "reset HEAD~1", "main", "Bash", command="git reset HEAD~1")
    expect_deny(repo, "reset --soft", "main", "Bash", command="git reset --soft HEAD^")
    expect_deny(repo, "filter-branch", "main", "Bash", command="git filter-branch --tree-filter 'rm -f x' HEAD")
    expect_deny(repo, "update-ref", "main", "Bash", command="git update-ref refs/heads/claude/feature-x abc123")
    expect_deny(repo, "gh pr merge", "main", "Bash", command="gh pr merge 12 --squash")
    expect_deny(repo, "gh api merge", "main", "Bash", command="gh api -X PUT repos/o/r/pulls/12/merge")
    expect_deny(repo, "gh merge in chain", "main", "Bash", command="./gradlew gauntlet && gh pr merge --auto")
    # --- specification: before any spec commit (draft)
    check("not frozen initially", git(repo, "rev-list", "--count", "origin/main..HEAD", "--", FEATURES) == "0")
    expect_allow(repo, "specifier writes draft", "specifier", "Write", file_path=feature, content="# language: ru\n")
    expect_deny(repo, "main agent writes feature", "main", "Write", file_path=feature, content="x")
    expect_deny(repo, "specifier writes code", "specifier", "Write", file_path=M + "src/main/java/A.java", content="x")
    expect_deny(repo, "specifier edits gauntlet", "specifier", "Edit", file_path="gauntlet/CLAUDE.md", old_string="a", new_string="b")
    expect_allow(repo, "specifier bash on features", "specifier", "Bash", command=f"cp /tmp/x.feature {feature}")
    expect_deny(repo, "main bash on features", "main", "Bash", command=f"sed -i 's/10/20/' {feature}")
    expect_deny(repo, "hardener writes feature", "hardener", "Write", file_path=feature, content="x")
    if len(MODULES) > 1:
        other = MODULES[1] + "/" + FEATURES_REL + "/mapping.feature"
        expect_allow(repo, "specifier writes the other module's draft", "specifier", "Write", file_path=other, content="x")
        expect_deny(repo, "main writes the other module's feature", "main", "Write", file_path=other, content="x")
        expect_allow(repo, "hardener writes the other module's tests", "hardener", "Write",
                     file_path=MODULES[1] + "/src/test/java/com/example/MappingTest.java", content="x")
        expect_deny(repo, "hardener writes the other module's main", "hardener", "Write",
                    file_path=MODULES[1] + "/src/main/java/com/example/Mapper.java", content="x")
    # --- specification: after the spec commit (approved -> frozen)
    write(repo, feature, "# language: ru\nФункция: Заказ\n")
    git(repo, "add", "-A")
    git(repo, "commit", "-q", "-m", "spec: order flow")
    check("frozen after spec commit", git(repo, "rev-list", "--count", "origin/main..HEAD", "--", FEATURES) == "1")
    r = guard(repo, "specifier", "Edit", file_path=feature, old_string="a", new_string="b")
    check("specifier blocked when frozen", r is not None and "frozen" in r, repr(r))
    expect_deny(repo, "specifier new feature file when frozen", "specifier", "Write", file_path=FEATURES + "/other.feature", content="x")
    expect_deny(repo, "main blocked when frozen", "main", "Edit", file_path=feature, old_string="a", new_string="b")
    r = guard(repo, "main", "Bash", command=f"git rm {feature}")
    check("git rm feature when frozen", r is not None and "frozen" in r, repr(r))
    expect_deny(repo, "specifier bash on frozen features", "specifier", "Bash", command=f"cp /tmp/x.feature {feature}")
    if len(MODULES) > 1:
        other = MODULES[1] + "/" + FEATURES_REL + "/mapping.feature"
        r = guard(repo, "specifier", "Write", file_path=other, content="x")
        check("other module's spec frozen by core's spec commit", r is not None and "frozen" in r, repr(r))
        expect_deny(repo, "rm other module's acceptance dir when frozen", "main", "Bash",
                    command=f"rm -rf {MODULES[1]}/src/acceptanceTest")
    expect_deny(repo, "rm -rf src when frozen", "main", "Bash", command=f"rm -rf {M}src/acceptanceTest/resources")
    expect_deny(repo, "git rm -r src/acceptanceTest when frozen", "main", "Bash", command=f"git rm -r {M}src/acceptanceTest")
    expect_deny(repo, "git checkout base -- src when frozen", "main", "Bash", command=f"git checkout origin/main -- {M}src")
    expect_deny(repo, "cd then cp into features when frozen", "specifier", "Bash",
                command=f"cd {M}src/acceptanceTest/resources && cp /tmp/x.feature features/order.feature")
    in_features = os.path.join(repo, FEATURES)
    r = guard(repo, "main", "Bash", cwd=in_features, command="sed -i 's/a/b/' order.feature")
    check("shell already in the features dir: sed -i on a bare name when frozen", r is not None and "frozen" in r, repr(r))
    r = guard(repo, "main", "Bash", cwd=in_features, command="cp /tmp/x.feature ./order.feature")
    check("shell already in the features dir: cp onto a bare name when frozen", r is not None and "frozen" in r, repr(r))
    r = guard(repo, "main", "Bash", cwd=os.path.join(repo, M + "src"), command="echo x > acceptanceTest/resources/features/order.feature")
    check("shell already in src: redirect into features when frozen", r is not None and "frozen" in r, repr(r))
    r = guard(repo, "main", "Bash", cwd=in_features, command="python3 -c \"open('order.feature', 'a').write('x')\"")
    check("shell already in the features dir: inline python write when frozen", r is not None and "frozen" in r, repr(r))
    expect_allow(repo, "shell already in the features dir: reading is fine", "main", "Bash", command="cat order.feature")
    r = guard(repo, "main", "Bash", cwd=os.path.join(repo, "gauntlet"), command="sed -i 's/a/b/' CLAUDE.md")
    check("shell already in gauntlet/: sed -i on its CLAUDE.md", r is not None, repr(r))
    head = git(repo, "rev-parse", "HEAD")
    git(repo, "checkout", "-q", "--detach", "origin/main")
    expect_deny(repo, "detached HEAD at base is frozen", "specifier", "Write", file_path=FEATURES + "/other.feature", content="x")
    git(repo, "checkout", "-q", "claude/feature-x")
    check("back on the branch", git(repo, "rev-parse", "HEAD") == head)
    expect_allow(repo, "rm test dir when frozen", "main", "Bash", command=f"rm -rf {M}src/test/java/com/example/service/domain/Old.java")
    expect_allow(repo, "code edits still fine when frozen", "main", "Edit",
                 file_path=M + "src/main/java/com/example/service/domain/Order.java", old_string="a", new_string="b")
    # --- roles
    expect_allow(repo, "hardener writes unit test", "hardener", "Write", file_path=M + "src/test/java/com/example/OrderTest.java", content="x")
    expect_allow(repo, "hardener writes step defs", "hardener", "Edit", file_path=M + "src/acceptanceTest/java/com/example/Steps.java",
                 old_string="a", new_string="b")
    expect_deny(repo, "hardener writes main", "hardener", "Edit", file_path=M + "src/main/java/com/example/service/domain/Order.java",
                old_string="a", new_string="b")
    expect_deny(repo, "hardener bash on main", "hardener", "Bash", command=f"sed -i 's/a/b/' {M}src/main/java/com/example/service/domain/Order.java")
    expect_allow(repo, "hardener runs pit", "hardener", "Bash", command="./gradlew mutationGate --continue")
    expect_deny(repo, "hardener sed on build script", "hardener", "Bash", command=f"sed -i 's/x/y/' {M}build.gradle.kts")
    expect_deny(repo, "hardener cp into resources", "hardener", "Bash", command=f"cp /tmp/a {M}src/acceptanceTest/resources/x.properties")
    expect_allow(repo, "hardener cp within tests", "hardener", "Bash",
                 command=f"cp {M}src/test/java/com/example/service/domain/OrderTest.java {M}src/test/java/com/example/service/domain/OrderBoundaryTest.java")
    expect_allow(repo, "hardener mkdir tests", "hardener", "Bash", command=f"mkdir -p {M}src/test/java/com/example/service/api")
    expect_deny(repo, "reviewer writes", "reviewer", "Write", file_path=M + "src/main/java/A.java", content="x")
    expect_deny(repo, "reviewer git add", "reviewer", "Bash", command="git add -A")
    expect_deny(repo, "reviewer git stash", "reviewer", "Bash", command="git stash")
    expect_deny(repo, "reviewer redirect", "reviewer", "Bash", command="echo x > notes.txt")
    expect_allow(repo, "reviewer redirect outside project", "reviewer", "Bash", command="echo x > /tmp/notes.txt")
    expect_allow(repo, "reviewer reads diff", "reviewer", "Bash", command=f"git diff $(git merge-base origin/main HEAD) -- {M}src")
    expect_allow(repo, "reviewer runs gauntletFast", "reviewer", "Bash", command="./gradlew gauntletFast --continue")
    expect_allow(repo, "reviewer branch --show-current", "reviewer", "Bash", command="git branch --show-current")
    expect_allow(repo, "reviewer branch -a", "reviewer", "Bash", command="git branch -a")
    expect_allow(repo, "reviewer stash list", "reviewer", "Bash", command="git stash list")
    expect_allow(repo, "reviewer tag -l", "reviewer", "Bash", command="git tag -l 'v*'")
    expect_deny(repo, "reviewer creates branch", "reviewer", "Bash", command="git branch scratch")
    expect_deny(repo, "reviewer tags", "reviewer", "Bash", command="git tag v9")
    expect_deny(repo, "reviewer mkdir", "reviewer", "Bash", command="mkdir notes")
    # --- after the PR is merged (base holds the same feature files) the branch is not frozen any more
    git(repo, "push", "-q", "origin", "HEAD:refs/heads/merged-main")
    props = os.path.join(repo, "gauntlet/config/gauntlet.properties")
    with open(props) as f:
        original = f.read()
    with open(props, "w") as f:
        f.write(original.replace("baseRef=origin/main", "baseRef=origin/merged-main"))
    expect_allow(repo, "merged spec -> specifier may edit again", "specifier", "Edit", file_path=feature, old_string="a", new_string="b")
    with open(props, "w") as f:
        f.write(original)
    # --- spec.approval=pr: no freeze
    props = os.path.join(repo, "gauntlet/config/gauntlet.properties")
    with open(props) as f:
        original = f.read()
    with open(props, "w") as f:
        f.write(original.replace("spec.approval=chat", "spec.approval=pr"))
    expect_allow(repo, "pr mode: specifier edits committed feature", "specifier", "Edit", file_path=feature, old_string="a", new_string="b")
    expect_deny(repo, "pr mode: main still cannot write features", "main", "Edit", file_path=feature, old_string="a", new_string="b")
    with open(props, "w") as f:
        f.write(original)
    # --- guard off (human's admin environment)
    e = {"session_id": "s1", "cwd": repo, "tool_name": "Edit",
         "tool_input": {"file_path": "gauntlet/config/gauntlet.properties", "old_string": "a", "new_string": "b"}}
    r = run_hook(repo, "guard.py", e, env={"GAUNTLET_GUARD": "off"})
    check("GAUNTLET_GUARD=off allows gauntlet edits", r.returncode == 0 and not r.stdout.strip(), r.stdout)
    # --- fail open on garbage
    r = subprocess.run([sys.executable, os.path.join(repo, "gauntlet/hooks/guard.py")], input="not json", cwd=repo,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    check("fails open on malformed input", r.returncode == 0 and not r.stdout.strip())


def stop_tests(repo):
    """Runs on a fresh branch state: HEAD = spec commit, clean tree."""
    def stop(session="s2", fake_exit="0", stop_hook_active=False):
        os.makedirs(os.path.join(repo, M + "build/gauntlet"), exist_ok=True)
        write(repo, M + "build/gauntlet/fake-exit", fake_exit)
        event = {"session_id": session, "cwd": repo, "hook_event_name": "Stop", "stop_hook_active": stop_hook_active}
        return run_hook(repo, "stop_gate.py", event, args=("--task", "gauntletFast", "--max-attempts", "3", "--timeout", "60"))

    def record(session="s2"):
        return run_hook(repo, "stop_gate.py", {"session_id": session, "cwd": repo, "hook_event_name": "SessionStart"},
                        args=("--record-start",))

    def log():
        try:
            with open(os.path.join(repo, M + "build/gauntlet/gauntletFast-hook.log")) as f:
                return f.read()
        except OSError:
            return ""

    git(repo, "checkout", "-q", "main")
    git(repo, "checkout", "-q", "-b", "claude/feature-y")
    record()
    r = stop()
    check("no change -> no run", r.returncode == 0 and "fake gradle" not in log(), r.stderr)
    # spec-only turn: never runs the gate
    write(repo, FEATURES + "/draft.feature", "# language: ru\nФункция: Черновик\n")
    r = stop()
    check("spec-only change -> no run", r.returncode == 0 and "fake gradle" not in log(), r.stderr)
    # code change while the spec is still a draft: run without the acceptance tasks
    write(repo, M + "src/main/java/com/example/service/domain/Order.java", "package x; class Order {}\n")
    r = stop()
    check("pending spec -> acceptance excluded", r.returncode == 0 and "-x acceptanceTest -x acceptanceCheck" in log(), log())
    check("pending spec -> note to agent", "human's approval" in r.stdout, r.stdout)
    # commit the spec: acceptance tasks come back
    git(repo, "add", FEATURES)
    git(repo, "commit", "-q", "-m", "spec: draft")
    write(repo, M + "src/main/java/com/example/service/domain/Order.java", "package x; class Order { int a; }\n")
    r = stop()
    check("committed spec -> full gauntletFast", r.returncode == 0 and "-x acceptanceTest" not in log(), log())
    # red: blocked, and re-run only when something changed
    write(repo, M + "src/main/java/com/example/service/domain/Order.java", "package x; class Order { int b; }\n")
    r = stop(fake_exit="1")
    check("red -> exit 2 with reasons", r.returncode == 2 and "CRAP FAIL" in r.stderr and "attempt 1/3" in r.stderr, r.stderr)
    os.remove(os.path.join(repo, M + "build/gauntlet/fake-exit"))   # fake gradle would now be green if it ran
    r = stop(stop_hook_active=True)
    check("same tree -> cached red, no re-run", r.returncode == 2 and "attempt 2/3" in r.stderr, r.stderr)
    r = stop(stop_hook_active=True)
    check("attempt 3 still blocks", r.returncode == 2 and "attempt 3/3" in r.stderr, r.stderr)
    r = stop(stop_hook_active=True)
    check("attempt limit -> let go with warning", r.returncode == 0 and "still RED" in r.stdout, r.stdout + r.stderr)
    # fix -> green -> cached green
    write(repo, M + "src/main/java/com/example/service/domain/Order.java", "package x; class Order { int c; }\n")
    r = stop()
    check("changed tree -> re-run, green", r.returncode == 0 and "fake gradle gauntletFast" in log(), log())
    os.remove(os.path.join(repo, M + "build/gauntlet/gauntletFast-hook.log"))
    r = stop()
    check("green cached -> no re-run", r.returncode == 0 and not os.path.exists(os.path.join(repo, M + "build/gauntlet/gauntletFast-hook.log")))
    # a later spec-only turn (code unchanged since the last allowed stop) never waits for Gradle
    write(repo, FEATURES + "/second.feature", "# language: ru\nФункция: Вторая\n")
    if os.path.exists(os.path.join(repo, M + "build/gauntlet/gauntletFast-hook.log")):
        os.remove(os.path.join(repo, M + "build/gauntlet/gauntletFast-hook.log"))
    r = stop(fake_exit="1")
    check("spec-only turn after code turn -> no run", r.returncode == 0
          and not os.path.exists(os.path.join(repo, M + "build/gauntlet/gauntletFast-hook.log")), r.stderr)
    # after a green stop the agent's own Gradle runs change nothing: still no re-run
    time.sleep(1.1)
    write(repo, M + "build/test-results/test/TEST-x.xml", "<testsuite/>")
    r = stop(fake_exit="1")
    check("green mark + newer reports -> still no run", r.returncode == 0 and not os.path.exists(
        os.path.join(repo, M + "build/gauntlet/gauntletFast-hook.log")), r.stderr)
    # a released RED is remembered: later turns on the same code get a reminder, not silence
    git(repo, "add", FEATURES)
    git(repo, "commit", "-q", "-m", "spec: second (for the red scenario)")
    write(repo, M + "src/main/java/com/example/service/domain/Order.java", "package x; class Order { int e; }\n")
    for _ in range(3):
        r = stop(fake_exit="1", stop_hook_active=True)
    r = stop(fake_exit="1", stop_hook_active=True)
    check("red released after 3 attempts (2nd scenario)", r.returncode == 0 and "still RED" in r.stdout, r.stdout + r.stderr)
    r = stop(fake_exit="1")
    check("same red code next turn -> reminder", r.returncode == 0 and "reminder" in r.stdout, r.stdout + r.stderr)
    # ... a spec draft waiting for the human goes first even if the specifier ran Gradle
    write(repo, FEATURES + "/third.feature", "# language: ru\nФункция: Третья\n")
    time.sleep(1.1)
    write(repo, M + "build/test-results/acceptanceTest/TEST-z.xml", "<testsuite/>")
    r = stop(fake_exit="1")
    check("draft spec after released red -> question passes with reminder", r.returncode == 0 and "reminder" in r.stdout
          and "attempt" not in r.stderr, r.stdout + r.stderr)
    git(repo, "add", FEATURES)
    git(repo, "commit", "-q", "-m", "spec: third")
    # ... the agent fixed the environment and ran Gradle itself (newer reports, no draft): re-run, now green
    time.sleep(1.1)
    write(repo, M + "build/test-results/test/TEST-fixed.xml", "<testsuite/>")
    r = stop()
    check("released red + newer reports -> re-run -> green", r.returncode == 0 and "reminder" not in r.stdout
          and "fake gradle gauntletFast" in log(), r.stdout + r.stderr + log())
    # green while the spec was a draft, then the spec gets committed with no code change: acceptance must run now
    write(repo, FEATURES + "/fourth.feature", "# language: ru\nФункция: Четвёртая\n")
    write(repo, M + "src/main/java/com/example/service/domain/Order.java", "package x; class Order { int f; }\n")
    r = stop()
    check("draft + code -> green with acceptance excluded", r.returncode == 0 and "-x acceptanceTest" in log(), log())
    git(repo, "add", FEATURES)
    git(repo, "commit", "-q", "-m", "spec: fourth")
    r = stop(fake_exit="1")
    check("pending -> approved with same code re-checks", r.returncode == 2 and "-x acceptanceTest" not in log(), r.stderr + log())
    os.remove(os.path.join(repo, M + "build/gauntlet/fake-exit"))
    # SubagentStop (hardener, from settings.json): stop_hook_active there only says whether the subagent has Stop hooks
    # of its own, so it is always false - retries within the window must still count up to the limit and let go
    write(repo, M + "src/main/java/com/example/service/domain/Order.java", "package x; class Order { int g; }\n")
    write(repo, M + "build/gauntlet/fake-exit", "1")
    sub = {"session_id": "s2", "cwd": repo, "hook_event_name": "SubagentStop", "agent_type": "hardener",
           "agent_id": "h1", "stop_hook_active": False}
    sub_args = ("--task", "mutationGate", "--max-attempts", "2", "--timeout", "60")
    r = run_hook(repo, "stop_gate.py", sub, args=sub_args)
    check("subagent red -> blocked, attempt 1/2", r.returncode == 2 and "attempt 1/2" in r.stderr, r.stderr)
    r = run_hook(repo, "stop_gate.py", sub, args=sub_args)
    check("subagent retry counts although stop_hook_active=false", r.returncode == 2 and "attempt 2/2" in r.stderr,
          r.stderr)
    r = run_hook(repo, "stop_gate.py", sub, args=sub_args)
    check("subagent attempt limit -> let go with warning", r.returncode == 0 and "still RED" in r.stdout,
          r.stdout + r.stderr)
    os.remove(os.path.join(repo, M + "build/gauntlet/fake-exit"))
    if not MODULES:
        subagent_window_tests(repo)
    # switched off by the human
    write(repo, M + "src/main/java/com/example/service/domain/Order.java", "package x; class Order { int d; }\n")
    write(repo, M + "build/gauntlet/fake-exit", "1")
    e = {"session_id": "s2", "cwd": repo, "hook_event_name": "Stop"}
    r = run_hook(repo, "stop_gate.py", e, env={"GAUNTLET_STOP_HOOK": "off"}, args=("--task", "gauntletFast"))
    check("GAUNTLET_STOP_HOOK=off", r.returncode == 0)


def subagent_window_tests(repo):
    """The SubagentStop retry window counts the time the agent worked since the last block, from the start of the
    hook: a Gradle run of the hook itself (mutationGate) must not push a quick retry out of the window."""
    state = os.path.join(repo, git(repo, "rev-parse", "--git-path", "gauntlet-hook"))
    attempts = os.path.join(state, "s2-h2.attempts")
    sub = {"session_id": "s2", "cwd": repo, "hook_event_name": "SubagentStop", "agent_type": "hardener",
           "agent_id": "h2", "stop_hook_active": False}
    sub_args = ("--task", "mutationGate", "--max-attempts", "2", "--timeout", "60")
    window = 20 * 60
    write(repo, M + "build/gauntlet/fake-exit", "1")
    write(repo, M + "build/gauntlet/fake-sleep", "4")
    write(repo, M + "src/main/java/com/example/service/domain/Order.java", "package x; class Order { int w; }\n")
    with open(attempts, "w") as f:
        f.write(f"1 {time.time() - window + 2}")
    r = run_hook(repo, "stop_gate.py", sub, args=sub_args)
    check("subagent window counts from the hook start, not after Gradle", r.returncode == 2 and "attempt 2/2" in r.stderr,
          r.stderr)
    os.remove(os.path.join(repo, M + "build/gauntlet/fake-sleep"))
    write(repo, M + "src/main/java/com/example/service/domain/Order.java", "package x; class Order { int v; }\n")
    with open(attempts, "w") as f:
        f.write(f"1 {time.time() - window - 5}")
    r = run_hook(repo, "stop_gate.py", sub, args=sub_args)
    check("subagent retry after the window starts again at 1", r.returncode == 2 and "attempt 1/2" in r.stderr, r.stderr)
    os.remove(attempts)
    os.remove(os.path.join(repo, M + "build/gauntlet/fake-exit"))


def multi_module_stop_tests(repo):
    """Two modules: Gradle runs only where something changed; a red module blocks."""
    core, adapter = MODULES[0], MODULES[1]

    def stop(stop_hook_active=False):
        event = {"session_id": "s3", "cwd": repo, "hook_event_name": "Stop", "stop_hook_active": stop_hook_active}
        return run_hook(repo, "stop_gate.py", event, args=("--task", "gauntletFast", "--max-attempts", "3", "--timeout", "60"))

    def log_of(module):
        path = os.path.join(repo, module, "build/gauntlet/gauntletFast-hook.log")
        try:
            with open(path) as f:
                return f.read()
        except OSError:
            return ""

    def clear_logs():
        for m in (core, adapter):
            shutil.rmtree(os.path.join(repo, m, "build"), ignore_errors=True)

    sh(repo, "git", "stash", "-u", "-q")             # leftovers of the previous scenario
    git(repo, "checkout", "-q", "main")
    git(repo, "checkout", "-q", "-b", "claude/feature-z")
    # the developer's own uncommitted work, present before the session, must not count as the agent's change
    write(repo, core + "/src/main/java/com/example/service/domain/Draft.java", "package x; class Draft {}\n")
    run_hook(repo, "stop_gate.py", {"session_id": "s3", "cwd": repo, "hook_event_name": "SessionStart"}, args=("--record-start",))
    clear_logs()
    write(repo, adapter + "/src/main/java/com/example/service/domain/Mapper.java", "package x; class Mapper {}\n")
    r = stop()
    check("adapter change -> gradle in adapter only (pre-existing dirty core file ignored)", r.returncode == 0
          and "fake gradle" in log_of(adapter) and not log_of(core), r.stderr + log_of(core))
    clear_logs()
    write(repo, core + "/src/main/java/com/example/service/domain/Order.java", "package x; class Order { int z; }\n")
    r = stop()
    check("both changed -> gradle in both", r.returncode == 0 and "fake gradle" in log_of(core) and "fake gradle" in log_of(adapter),
          r.stderr)
    check("lock wait fits the budget (half of --timeout 60)", re.search(r"-Pgauntlet\.lockWaitSeconds=(29|30)\b", log_of(core))
          and "-Pgauntlet.lockWaitSeconds=" in log_of(adapter), log_of(core) + log_of(adapter))
    # one budget for all modules: a slow first module leaves no time for the second, which is not started
    clear_logs()
    write(repo, core + "/build/gauntlet/fake-sleep", "3")
    write(repo, core + "/src/main/java/com/example/service/domain/Order.java", "package x; class Order { int y; }\n")
    write(repo, adapter + "/src/main/java/com/example/service/domain/Mapper.java", "package x; class Mapper { int y; }\n")
    event = {"session_id": "s3", "cwd": repo, "hook_event_name": "Stop", "stop_hook_active": False}
    r = run_hook(repo, "stop_gate.py", event, args=("--task", "gauntletFast", "--max-attempts", "3", "--timeout", "2"))
    check("budget spent in the first module -> red, second module not run", r.returncode == 2
          and "did not finish within" in log_of(core) and "no time left" in log_of(adapter)
          and "fake gradle" not in log_of(adapter), r.stderr + log_of(core) + log_of(adapter))
    clear_logs()
    write(repo, core + "/src/main/java/com/example/service/domain/Order.java", "package x; class Order { int z; }\n")
    write(repo, adapter + "/src/main/java/com/example/service/domain/Mapper.java", "package x; class Mapper {}\n")
    r = stop(stop_hook_active=True)
    check("budget test cleaned up -> green", r.returncode == 0, r.stderr)
    write(repo, adapter + "/build/gauntlet/fake-exit", "1")
    write(repo, adapter + "/src/main/java/com/example/service/domain/Mapper.java", "package x; class Mapper { int a; }\n")
    r = stop()
    check("red adapter blocks", r.returncode == 2 and f"=== {adapter} ===" in r.stderr and "CRAP FAIL" in r.stderr, r.stderr)
    os.remove(os.path.join(repo, adapter, "build/gauntlet/fake-exit"))
    write(repo, adapter + "/src/main/java/com/example/service/domain/Mapper.java", "package x; class Mapper { int b; }\n")
    r = stop(stop_hook_active=True)
    check("fixed adapter -> green", r.returncode == 0, r.stderr)


def test_inputs_stop_tests(repo, key, document, session):
    """A file the tests read outside their source sets (acceptance.inputs, test.inputs) is code for the gate; any other
    document is not. The setting is per module (gauntlet/config/<module>/gauntlet.properties) or, for a build at the
    root, in the common file."""
    def stop():
        event = {"session_id": session, "cwd": repo, "hook_event_name": "Stop", "stop_hook_active": False}
        return run_hook(repo, "stop_gate.py", event, args=("--task", "gauntletFast", "--max-attempts", "3", "--timeout", "60"))

    def log_of(prefix):
        try:
            with open(os.path.join(repo, prefix + "build/gauntlet/gauntletFast-hook.log")) as f:
                return f.read()
        except OSError:
            return ""

    others = [m + "/" for m in MODULES[1:]]
    sh(repo, "git", "stash", "-u", "-q")             # leftovers of the previous scenario
    git(repo, "checkout", "-q", "main")
    git(repo, "checkout", "-q", "-b", "claude/feature-docs-" + session)
    if MODULES:
        write(repo, "gauntlet/config/" + MODULES[0] + "/gauntlet.properties", f"{key}={document}\n")
    else:
        with open(os.path.join(repo, "gauntlet/config/gauntlet.properties"), "a") as f:
            f.write(f"{key}={document}\n")
    for prefix in [M] + others:
        write(repo, prefix + document, "# Document the tests read\n")
    write(repo, M + "docs/design.md", "# Design\n")
    git(repo, "add", "-A")
    git(repo, "commit", "-q", "-m", "documents and test inputs")
    for prefix in [M] + others:
        shutil.rmtree(os.path.join(repo, prefix + "build"), ignore_errors=True)
    run_hook(repo, "stop_gate.py", {"session_id": session, "cwd": repo, "hook_event_name": "SessionStart"},
             args=("--record-start",))
    write(repo, M + "docs/design.md", "# Design\nchanged\n")
    r = stop()
    check(f"{key}: another document changed -> no run", r.returncode == 0 and "fake gradle" not in log_of(M),
          r.stderr + log_of(M))
    write(repo, M + document, "# Document the tests read\nchanged\n")
    r = stop()
    check(f"{key}: {document} changed -> gradle runs", r.returncode == 0 and "fake gradle" in log_of(M),
          r.stderr + log_of(M))
    shutil.rmtree(os.path.join(repo, M + "build"), ignore_errors=True)
    os.remove(os.path.join(repo, M + document))
    r = stop()
    check(f"{key}: {document} deleted -> gradle runs", r.returncode == 0 and "fake gradle" in log_of(M),
          r.stderr + log_of(M))
    for prefix in others:
        write(repo, prefix + document, "# Document the tests read\nchanged\n")
        r = stop()
        check(f"{key}: the same file in a module without the setting -> no run there",
              r.returncode == 0 and "fake gradle" not in log_of(prefix), r.stderr + log_of(prefix))


def workflow_paths_tests():
    """In the repository the kit is installed in: every file a module's tests read outside the source sets
    (acceptance.inputs, test.inputs) is listed by name at the end of the CI `paths` filter, after the '!' patterns - so
    that no exclusion of documents, today's or a later one, makes CI skip a change to it."""
    workflow = os.path.join(os.path.dirname(KIT), ".github", "workflows", "gauntlet.yml")
    if not os.path.isfile(workflow):
        return
    with open(workflow) as f:
        block = f.read().split("paths:", 1)[-1].split("workflow_dispatch", 1)[0]
    patterns = re.findall(r"^\s+-\s+'([^']+)'", block, re.M)
    negatives = [i for i, p in enumerate(patterns) if p.startswith("!")]
    tail = patterns[(negatives[-1] + 1) if negatives else 0:]
    config = os.path.join(KIT, "config")
    for module in sorted(os.listdir(config)):
        props = os.path.join(config, module, "gauntlet.properties")
        if not os.path.isfile(props):
            continue
        with open(props) as f:
            values = [line.split("=", 1)[1] for line in f
                      if line.strip().startswith(("acceptance.inputs=", "test.inputs="))]
        for entry in [e.strip() for v in values for e in v.split(",") if e.strip()]:
            check(f"CI paths list {module}/{entry} after the '!' patterns", f"{module}/{entry}" in tail,
                  str(patterns))


def run_session_start(repo, gradle_home, remote=True):
    env = dict(os.environ, CLAUDE_PROJECT_DIR=repo, GRADLE_USER_HOME=gradle_home, GAUNTLET_DOCKER="off")
    env.pop("CLAUDE_CODE_REMOTE", None)
    if gradle_home is None:
        env.pop("GRADLE_USER_HOME")
        env.pop("HOME", None)
    if remote:
        env["CLAUDE_CODE_REMOTE"] = "true"
    return subprocess.run(["bash", os.path.join(repo, "gauntlet/hooks/session_start.sh")], cwd=repo, input="{}",
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=env)


def gradle_properties_tests(repo):
    root = os.path.dirname(repo)
    local_home = os.path.join(root, "gradle-local")
    r = run_session_start(repo, local_home, remote=False)
    check("session_start outside the cloud leaves the Gradle user home alone",
          r.returncode == 0 and not os.path.exists(local_home), r.stdout + r.stderr)
    home = os.path.join(root, "gradle-cloud")
    props = os.path.join(home, "gradle.properties")
    r = run_session_start(repo, home)
    check("session_start creates gradle.properties with publicRepositories=true",
          r.returncode == 0 and open(props).read() == "publicRepositories=true\n", r.stdout + r.stderr)
    run_session_start(repo, home)
    check("session_start does not duplicate publicRepositories", open(props).read() == "publicRepositories=true\n")
    with open(props, "w") as f:
        f.write("org.gradle.jvmargs=-Xmx2g")
    run_session_start(repo, home)
    check("session_start appends to an existing gradle.properties and keeps other lines",
          open(props).read() == "org.gradle.jvmargs=-Xmx2g\npublicRepositories=true\n", open(props).read())
    with open(props, "w") as f:
        f.write("publicRepositories=false\n")
    r = run_session_start(repo, home)
    check("session_start keeps an explicit publicRepositories value and warns that it is not true",
          open(props).read() == "publicRepositories=false\n" and "sets publicRepositories to a value other than true"
          in r.stdout, r.stdout + r.stderr)
    with open(props, "w") as f:
        f.write("publicRepositories false\n")
    r = run_session_start(repo, home)
    check("session_start recognises a whitespace-separated publicRepositories key",
          open(props).read() == "publicRepositories false\n" and "other than true" in r.stdout, r.stdout + r.stderr)
    with open(props, "w") as f:
        f.write("publicRepositories : true\n")
    r = run_session_start(repo, home)
    check("session_start accepts an explicit true without a warning",
          open(props).read() == "publicRepositories : true\n" and "WARNING" not in r.stdout, r.stdout + r.stderr)
    blocker = os.path.join(root, "not-a-directory")
    with open(blocker, "w") as f:
        f.write("")
    r = run_session_start(repo, os.path.join(blocker, "gradle"))
    check("session_start warns when the Gradle user home is not writable and still reports its status",
          r.returncode == 0 and "could not write publicRepositories" in r.stdout and "[gauntlet] active" in r.stdout,
          r.stdout + r.stderr)
    with open(props, "w") as f:
        f.write("publicRepositories=true\npublicRepositories=false\n")
    r = run_session_start(repo, home)
    check("session_start warns when the last publicRepositories value is not true",
          "other than true" in r.stdout, r.stdout + r.stderr)
    stray = "/.gradle"
    stray_before = os.path.exists(stray)
    r = run_session_start(repo, None)
    stray_created = not stray_before and os.path.exists(stray)
    if stray_created:
        shutil.rmtree(stray, ignore_errors=True)
    check("session_start warns when neither GRADLE_USER_HOME nor HOME is set",
          r.returncode == 0 and "could not write publicRepositories" in r.stdout and not stray_created,
          r.stdout + r.stderr)


def frozen_commit_tests():
    """However the frozen spec was changed (a script, a patch, an interpreter fed on stdin - ways the edit checks
    cannot see), no git command may commit or push the change; merging the base branch still brings its approved specs,
    and the task's own remote branch brings the human's fixes."""
    repo = make_repo()
    root = os.path.dirname(repo)
    feature = FEATURES + "/order.feature"
    shared = FEATURES + "/shared.feature"
    code = M + "src/main/java/com/example/service/domain/Order.java"
    shared_lines = ["# language: ru", "Функция: Общая", "", "  Сценарий: первый", "    Дано a", "",
                    "  Сценарий: второй", "    Дано b", ""]
    try:
        git(repo, "checkout", "-q", "-b", "next-main", "origin/main")
        write(repo, shared, "\n".join(shared_lines))
        git(repo, "add", "-A")
        git(repo, "commit", "-q", "-m", "spec: shared, an earlier task")
        git(repo, "push", "-q", "origin", "next-main:main")
        git(repo, "checkout", "-q", "claude/feature-x")
        git(repo, "fetch", "-q", "origin")
        git(repo, "merge", "-q", "--ff-only", "origin/main")

        write(repo, feature, "# language: ru\nФункция: Заказ\n")
        write(repo, shared, "\n".join(["# language: ru", "Функция: Общая, дополнена задачей"] + shared_lines[2:]))
        git(repo, "add", "-A")
        expect_allow(repo, "spec commit itself (draft, not frozen yet)", "main", "Bash", command="git commit -m 'spec: order'")
        git(repo, "commit", "-q", "-m", "spec: order")
        spec_head = git(repo, "rev-parse", "HEAD")
        expect_allow(repo, "first push of the task branch", "main", "Bash", command="git push -u origin HEAD")
        git(repo, "push", "-q", "-u", "origin", "claude/feature-x")

        git(repo, "checkout", "-q", "-b", "human", "origin/claude/feature-x")
        write(repo, feature, "# language: ru\nФункция: Заказ, исправлен человеком\n")
        git(repo, "commit", "-q", "-am", "spec: fix by the human")
        git(repo, "push", "-q", "origin", "human:claude/feature-x")
        git(repo, "checkout", "-q", "claude/feature-x")
        git(repo, "branch", "-q", "-D", "human")
        git(repo, "fetch", "-q", "origin")
        for name, command in [("pull --ff-only", "git pull --ff-only"), ("merge @{u}", "git merge @{u}"),
                              ("merge the own remote branch", "git fetch origin && git merge origin/claude/feature-x")]:
            expect_allow(repo, f"frozen: human's spec fix from the own remote branch, {name}", "main", "Bash",
                         command=command)
        git(repo, "merge", "-q", "--ff-only", "origin/claude/feature-x")

        write(repo, feature, "# language: ru\nФункция: Заказ изменён скриптом\n")
        git(repo, "add", "-A")
        for name, command in [("commit", "git commit -m x"), ("commit -am", "git commit -am x"),
                              ("git -C repo commit", f"git -C {repo} commit -m x"),
                              ("cd repo && commit", f"cd {repo} && git commit -m x"),
                              ("bash -c commit", "bash -c 'git commit -m x'"),
                              ("commit after a heredoc script", "python3 - <<'EOF'\nprint(1)\nEOF\ngit commit -m x")]:
            r = guard(repo, "main", "Bash", command=command)
            check(f"frozen: staged spec change, {name}", r is not None and "frozen" in r, repr(r))
        git(repo, "reset", "-q")
        r = guard(repo, "main", "Bash", command="git commit -m code")
        check("frozen: unstaged spec change blocks any commit", r is not None and "frozen" in r, repr(r))
        r = guard(repo, "hardener", "Bash", command="git commit -m tests")
        check("frozen: hardener commit with a spec change", r is not None and "frozen" in r, repr(r))
        git(repo, "stash", "-q")
        r = guard(repo, "main", "Bash", command="git push origin 'stash@{0}:refs/heads/claude/feature-x'")
        check("frozen: push of a stash carrying a spec change", r is not None and "frozen" in r, repr(r))
        git(repo, "branch", "-q", "smuggle", "stash@{0}")
        r = guard(repo, "main", "Bash", command="git push origin smuggle:claude/feature-x")
        check("frozen: push of a branch made from a stash", r is not None and "frozen" in r, repr(r))
        git(repo, "branch", "-q", "-D", "smuggle")
        git(repo, "stash", "drop", "-q")
        expect_deny(repo, "frozen: push of every branch", "main", "Bash", command="git push --all origin")

        write(repo, code, "package com.example.service.domain;\npublic class Order { }\n")
        git(repo, "add", "-A")
        expect_allow(repo, "frozen: code-only commit", "main", "Bash", command="git commit -m 'code'")
        expect_allow(repo, "frozen: commit message about --work-tree in quotes", "main", "Bash",
                     command="git commit -m 'explain --work-tree and GIT_DIR=x'")
        expect_allow(repo, "frozen: heredoc message with a git am line", "main", "Bash",
                     command="git commit -F - <<'EOF'\ngit am notes\nEOF")
        expect_allow(repo, "frozen: git am mentioned in a quoted PR body", "main", "Bash",
                     command="gh pr create --title t --body 'Applied by hand, not with `git am`.'")
        expect_allow(repo, "frozen: commit message naming git cherry-pick", "main", "Bash",
                     command="git commit -m \"code\n\ngit cherry-pick HEAD~1 is not needed\"")
        expect_allow(repo, "frozen: read-only --git-dir next to a commit", "main", "Bash",
                     command="git rev-parse --git-dir && git commit -am code")
        expect_deny(repo, "frozen: commit with another work tree", "main", "Bash", command="GIT_WORK_TREE=/tmp/x git commit -a")
        expect_deny(repo, "frozen: --work-tree commit", "main", "Bash", command="git --work-tree=/tmp/x commit -am x")
        expect_deny(repo, "frozen: commit with another index", "main", "Bash", command="GIT_INDEX_FILE=/tmp/i git commit -m x")
        expect_deny(repo, "frozen: commit-tree", "main", "Bash", command="git commit-tree HEAD^{tree} -p HEAD -m x")
        expect_deny(repo, "frozen: fast-import", "main", "Bash", command="git fast-import < /tmp/stream")
        for form in ("--show-current-patch=diff", "-r", "--allow-empty"):
            expect_allow(repo, f"frozen: git am {form} without a spec change", "main", "Bash", command=f"git am {form}")
        git(repo, "commit", "-q", "-m", "code")
        expect_allow(repo, "frozen: push of code commits", "main", "Bash", command="git push")
        expect_allow(repo, "frozen: push -u origin HEAD", "main", "Bash", command="git push -u origin HEAD")
        r = run_hook(repo, "guard.py", {"tool_name": "Bash", "cwd": ["a"], "tool_input": {"command": "git commit -m x"}})
        check("frozen: malformed cwd fails open", r.returncode == 0 and not r.stdout.strip(), r.stdout + r.stderr)

        side = os.path.join(root, "side")
        git(repo, "worktree", "add", "-q", "-b", "side", side, "HEAD")
        write(side, feature, "# language: ru\nФункция: Заказ в другом рабочем каталоге\n")
        git(side, "add", "-A")
        r = guard(repo, "main", "Bash", command=f"git -C {side} commit -m x")
        check("frozen: commit in another worktree via -C", r is not None and "frozen" in r, repr(r))
        r = guard(repo, "main", "Bash", command=f"cd {side} && git commit -m x")
        check("frozen: commit in another worktree via cd", r is not None and "frozen" in r, repr(r))
        git(side, "commit", "-q", "-m", "spec change on a side branch")
        spec_sha = git(side, "rev-parse", "HEAD")
        write(side, code, "package com.example.service.domain;\npublic class Order { int x; }\n")
        git(side, "add", "-A")
        git(side, "commit", "-q", "-m", "code on a side branch")
        code_sha = git(side, "rev-parse", "HEAD")
        git(repo, "worktree", "remove", "--force", side)
        expect_deny(repo, "frozen: merge a branch that changes the spec", "main", "Bash", command="git merge side")
        expect_deny(repo, "frozen: cherry-pick a spec change", "main", "Bash", command=f"git cherry-pick {spec_sha}")
        expect_deny(repo, "frozen: cherry-pick a range with a spec change", "main", "Bash",
                    command=f"git cherry-pick {spec_head}..{code_sha}")
        expect_allow(repo, "frozen: cherry-pick a code change", "main", "Bash", command=f"git cherry-pick {code_sha}")
        expect_allow(repo, "frozen: merge --abort", "main", "Bash", command="git merge --abort")
        expect_allow(repo, "frozen: cherry-pick --abort", "main", "Bash", command="git cherry-pick --abort")

        patches = os.path.join(root, "patches")
        git(repo, "format-patch", "-q", "-1", spec_sha, "-o", patches)
        spec_patch = os.path.join(patches, os.listdir(patches)[0])
        git(repo, "format-patch", "-q", "-1", code_sha, "-o", os.path.join(root, "code-patches"))
        code_patch = os.path.join(root, "code-patches", os.listdir(os.path.join(root, "code-patches"))[0])
        expect_deny(repo, "frozen: am a patch that changes the spec", "main", "Bash", command=f"git am {spec_patch}")
        expect_allow(repo, "frozen: am a code patch", "main", "Bash", command=f"git am {code_patch}")
        expect_deny(repo, "frozen: am from a pipe", "main", "Bash", command=f"cat {code_patch} | git am")
        expect_deny(repo, "frozen: am from a heredoc", "main", "Bash", command="git am <<'EOF'\nFrom x\nEOF")
        expect_deny(repo, "frozen: am of a redirected spec patch", "main", "Bash", command=f"git am < {spec_patch}")
        expect_allow(repo, "frozen: am of a redirected code patch", "main", "Bash", command=f"git am < {code_patch}")
        expect_deny(repo, "frozen: am with remapped paths", "main", "Bash", command=f"git am --directory=x {code_patch}")
        expect_allow(repo, "frozen: am --abort", "main", "Bash", command="git am --abort")

        git(repo, "checkout", "-q", "next-main")
        git(repo, "merge", "-q", "--ff-only", "origin/main")
        write(repo, FEATURES + "/approved_elsewhere.feature", "# language: ru\nФункция: Уже утверждена в другой задаче\n")
        write(repo, shared, "\n".join(shared_lines[:-2] + ["    Дано b, уточнено другой задачей", ""]))
        write(repo, code, "package com.example.service.domain;\npublic class Order { long y; }\n")
        git(repo, "add", "-A")
        git(repo, "commit", "-q", "-m", "another task")
        git(repo, "push", "-q", "origin", "next-main:main")
        git(repo, "checkout", "-q", "claude/feature-x")
        git(repo, "fetch", "-q", "origin")
        expect_allow(repo, "frozen: cherry-pick a base commit", "main", "Bash", command="git cherry-pick origin/main")
        expect_allow(repo, "frozen: merge the base branch with another task's spec", "main", "Bash",
                     command="git fetch origin main && git merge origin/main")
        sh(repo, "git", "merge", "--no-commit", "--no-ff", "origin/main")
        write(repo, code, "package com.example.service.domain;\npublic class Order { long y; }\n")
        git(repo, "add", code)
        expect_allow(repo, "frozen: conclude the base merge (code conflict, shared spec merged by git)", "main", "Bash",
                     command="git commit -m 'merge main'")
        expect_allow(repo, "frozen: merge --continue on the base merge", "main", "Bash", command="git merge --continue")
        write(repo, FEATURES + "/approved_elsewhere.feature", "# language: ru\nФункция: Изменена при слиянии\n")
        r = guard(repo, "main", "Bash", command="git commit -m 'merge main'")
        check("frozen: base merge with a changed spec file", r is not None and "frozen" in r, repr(r))
        git(repo, "checkout", "-q", "MERGE_HEAD", "--", FEATURES + "/approved_elsewhere.feature")
        git(repo, "commit", "-q", "-m", "merge main")
        expect_allow(repo, "frozen: push after the base merge", "main", "Bash", command="git push")
        write(repo, feature, "# language: ru\nФункция: Заказ, закоммичен в обход\n")
        git(repo, "commit", "-q", "-am", "spec change that got past the commit check")
        r = guard(repo, "main", "Bash", command="git push")
        check("frozen: push of a committed spec change", r is not None and "frozen" in r, repr(r))
    finally:
        shutil.rmtree(root, ignore_errors=True)


def stale_base_tests():
    """The branch was cut from a base newer than the local remote-tracking ref: the base's own spec commit must not
    freeze the branch - the guard fetches the base before answering "frozen"; without the remote it stays frozen."""
    repo = make_repo()
    try:
        feature = FEATURES + "/order.feature"
        stale = git(repo, "rev-parse", "origin/main")
        git(repo, "checkout", "-q", "main")
        write(repo, FEATURES + "/merged.feature", "# language: ru\nФункция: Уже в основной ветке\n")
        git(repo, "add", "-A")
        git(repo, "commit", "-q", "-m", "spec: merged earlier")
        git(repo, "push", "-q", "origin", "main")
        git(repo, "checkout", "-q", "-B", "claude/feature-y", "main")
        git(repo, "update-ref", "refs/remotes/origin/main", stale)
        check("stale base: base's spec commit looks like the branch's",
              git(repo, "rev-list", "--count", "origin/main..HEAD", "--", FEATURES) == "1")
        expect_allow(repo, "stale base: specifier writes a draft after the guard fetches the base", "specifier", "Write",
                     file_path=feature, content="# language: ru\n")
        check("stale base: the guard moved origin/main to the remote's main",
              git(repo, "rev-parse", "origin/main") == git(repo, "rev-parse", "main"))
        git(repo, "update-ref", "refs/remotes/origin/main", stale)
        git(repo, "remote", "set-url", "origin", os.path.join(os.path.dirname(repo), "missing.git"))
        r = guard(repo, "specifier", "Write", file_path=feature, content="# language: ru\n")
        check("stale base, fetch fails: stays frozen", r is not None and "frozen" in r, repr(r))
    finally:
        shutil.rmtree(os.path.dirname(repo), ignore_errors=True)


def session_start_tests(repo):
    gradle_properties_tests(repo)
    gradle_home = os.path.join(os.path.dirname(repo), "gradle-home")
    r = run_session_start(repo, gradle_home)
    check("session_start reports active", r.returncode == 0 and "[gauntlet] active" in r.stdout, r.stdout + r.stderr)
    # base branch missing: simulate by pointing baseRef at a branch the remote does not have
    props = os.path.join(repo, "gauntlet/config/gauntlet.properties")
    with open(props) as f:
        original = f.read()
    with open(props, "w") as f:
        f.write(original.replace("baseRef=origin/main", "baseRef=origin/nowhere"))
    r = run_session_start(repo, gradle_home)
    check("session_start warns about missing base", r.returncode == 0 and "cannot find the merge base" in r.stdout,
          r.stdout + r.stderr)
    with open(props, "w") as f:
        f.write(original)
    # shallow clone of the bare origin: the hook must fetch enough history to find the merge base
    root = os.path.dirname(repo)
    shallow = os.path.join(root, "shallow")
    subprocess.run(["git", "clone", "-q", "--depth", "1", "--branch", "claude/feature-x",
                    "file://" + os.path.join(root, "origin.git"), shallow], check=True)
    check("clone is shallow and single-branch", git(shallow, "rev-parse", "--is-shallow-repository") == "true"
          and "origin/main" not in git(shallow, "branch", "-r"))
    r = run_session_start(shallow, gradle_home)
    check("session_start fetches the base in a shallow clone", "[gauntlet] active" in r.stdout, r.stdout + r.stderr)


def main():
    global M, MODULES, FEATURES
    for MODULES in ([], ["core"], ["core", "adapter"]):
        M = MODULES[0] + "/" if MODULES else ""
        FEATURES = M + FEATURES_REL
        print("layout: " + ("build at the repository root" if not MODULES else "builds in modules " + ", ".join(MODULES)))
        repo = make_repo()
        try:
            guard_tests(repo)
            git(repo, "push", "-q", "origin", "claude/feature-x")
            session_start_tests(repo)
            stop_tests(repo)
            if len(MODULES) > 1:
                multi_module_stop_tests(repo)
            test_inputs_stop_tests(repo, "acceptance.inputs", "docs/operations.md", "s5")
            test_inputs_stop_tests(repo, "test.inputs", "docs/openapi.yaml", "s6")
        finally:
            shutil.rmtree(os.path.dirname(repo), ignore_errors=True)
        stale_base_tests()
        frozen_commit_tests()
    workflow_paths_tests()
    for f in failures:
        print("FAIL", f)
    print(f"{passed} passed, {len(failures)} failed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

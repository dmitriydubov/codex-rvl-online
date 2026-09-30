#!/usr/bin/env python3
"""Self-test for the gauntlet hooks. Builds a throwaway git repository with a fake base branch and
runs guard.py / stop_gate.py against it the way Claude Code does (JSON on stdin, environment set).

    python3 gauntlet/hooks/selftest.py          # from any directory; needs git and python3

Gradle is stubbed with a fake ./gradlew that exits with the code found in a marker file, so the
Stop-gate logic is exercised without a JVM.
"""
import json
import os
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


def guard(repo, agent, tool, **params):
    event = {"session_id": "s1", "cwd": repo, "tool_name": tool, "tool_input": params}
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
    # switched off by the human
    write(repo, M + "src/main/java/com/example/service/domain/Order.java", "package x; class Order { int d; }\n")
    write(repo, M + "build/gauntlet/fake-exit", "1")
    e = {"session_id": "s2", "cwd": repo, "hook_event_name": "Stop"}
    r = run_hook(repo, "stop_gate.py", e, env={"GAUNTLET_STOP_HOOK": "off"}, args=("--task", "gauntletFast"))
    check("GAUNTLET_STOP_HOOK=off", r.returncode == 0)


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
    write(repo, adapter + "/build/gauntlet/fake-exit", "1")
    write(repo, adapter + "/src/main/java/com/example/service/domain/Mapper.java", "package x; class Mapper { int a; }\n")
    r = stop()
    check("red adapter blocks", r.returncode == 2 and f"=== {adapter} ===" in r.stderr and "CRAP FAIL" in r.stderr, r.stderr)
    os.remove(os.path.join(repo, adapter, "build/gauntlet/fake-exit"))
    write(repo, adapter + "/src/main/java/com/example/service/domain/Mapper.java", "package x; class Mapper { int b; }\n")
    r = stop(stop_hook_active=True)
    check("fixed adapter -> green", r.returncode == 0, r.stderr)


def session_start_tests(repo):
    r = subprocess.run(["bash", os.path.join(repo, "gauntlet/hooks/session_start.sh")], cwd=repo, input="{}",
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                       env=dict(os.environ, CLAUDE_PROJECT_DIR=repo, CLAUDE_CODE_REMOTE="true"))
    check("session_start reports active", r.returncode == 0 and "[gauntlet] active" in r.stdout, r.stdout + r.stderr)
    # base branch missing: simulate by pointing baseRef at a branch the remote does not have
    props = os.path.join(repo, "gauntlet/config/gauntlet.properties")
    with open(props) as f:
        original = f.read()
    with open(props, "w") as f:
        f.write(original.replace("baseRef=origin/main", "baseRef=origin/nowhere"))
    r = subprocess.run(["bash", os.path.join(repo, "gauntlet/hooks/session_start.sh")], cwd=repo, input="{}",
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                       env=dict(os.environ, CLAUDE_PROJECT_DIR=repo, CLAUDE_CODE_REMOTE="true"))
    check("session_start warns about missing base", r.returncode == 0 and "WARNING" in r.stdout, r.stdout + r.stderr)
    with open(props, "w") as f:
        f.write(original)
    # shallow clone of the bare origin: the hook must fetch enough history to find the merge base
    root = os.path.dirname(repo)
    shallow = os.path.join(root, "shallow")
    subprocess.run(["git", "clone", "-q", "--depth", "1", "--branch", "claude/feature-x",
                    "file://" + os.path.join(root, "origin.git"), shallow], check=True)
    check("clone is shallow and single-branch", git(shallow, "rev-parse", "--is-shallow-repository") == "true"
          and "origin/main" not in git(shallow, "branch", "-r"))
    r = subprocess.run(["bash", os.path.join(shallow, "gauntlet/hooks/session_start.sh")], cwd=shallow, input="{}",
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                       env=dict(os.environ, CLAUDE_PROJECT_DIR=shallow, CLAUDE_CODE_REMOTE="true"))
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
        finally:
            shutil.rmtree(os.path.dirname(repo), ignore_errors=True)
    for f in failures:
        print("FAIL", f)
    print(f"{passed} passed, {len(failures)} failed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

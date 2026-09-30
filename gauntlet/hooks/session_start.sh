#!/usr/bin/env bash
# SessionStart hook (local and cloud sessions).
#  1. In a cloud session make sure the base branch is reachable: the scoped checks need the merge base,
#     and a fresh clone may not have fetched it (or may be shallow).
#  2. Record the working tree so the Stop gate knows what this session changed.
#  3. Print a one-line status that lands in the agent's context.
input=$(cat)
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
props=gauntlet/config/gauntlet.properties
base=$(sed -n 's/^[[:space:]]*baseRef[[:space:]]*=[[:space:]]*//p' "$props" 2>/dev/null | tail -1)
base=${base:-origin/main}
remote=${base%%/*}
branch=${base#*/}

reachable() { git merge-base HEAD "$base" >/dev/null 2>&1; }

# An explicit refspec: a single-branch clone's configured refspec would not create origin/<base>.
fetch_base() { git fetch --quiet "$remote" "+refs/heads/$branch:refs/remotes/$remote/$branch" >/dev/null 2>&1; }

if [ "$CLAUDE_CODE_REMOTE" = "true" ] && [ "$remote" != "$base" ] && ! reachable; then
  fetch_base || true
  if ! reachable && [ "$(git rev-parse --is-shallow-repository 2>/dev/null)" = "true" ]; then
    git fetch --quiet --unshallow "$remote" >/dev/null 2>&1 || git fetch --quiet --deepen=1000 "$remote" >/dev/null 2>&1 || true
    fetch_base || true
  fi
fi

printf '%s' "$input" | python3 gauntlet/hooks/stop_gate.py --record-start

# Tests with a database or a broker run on Testcontainers and fail without Docker. In a cloud session the
# daemon is not running after the container starts (processes are not part of the environment snapshot), and
# `service docker start` fails on ulimit there, so start dockerd directly (it needs root) and wait at most
# about 30 s for it. GAUNTLET_DOCKER=off skips the start (the hooks' selftest sets it).
docker_up() { timeout 5 docker info >/dev/null 2>&1; }
docker_status=unavailable
if docker_up; then
  docker_status=up
elif pgrep -x dockerd >/dev/null 2>&1; then
  docker_status="starting (dockerd is running but not answering yet)"
elif [ "$CLAUDE_CODE_REMOTE" = "true" ] && [ "${GAUNTLET_DOCKER:-on}" != "off" ] && [ "$(id -u)" = 0 ] \
    && command -v dockerd >/dev/null 2>&1 && command -v setsid >/dev/null 2>&1; then
  # setsid + closed stdio: the daemon outlives the hook and does not hold its output open
  setsid nohup dockerd --host=unix:///var/run/docker.sock </dev/null >>/tmp/dockerd.log 2>&1 &
  daemon=$!
  docker_status="not started (see /tmp/dockerd.log)"
  deadline=$((SECONDS + 30))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if docker_up; then docker_status=up; break; fi
    kill -0 "$daemon" 2>/dev/null || break
    sleep 1
  done
fi

if reachable; then
  echo "[gauntlet] active - scope: changed vs $base; guard: ${GAUNTLET_GUARD:-on}; stop hook: ${GAUNTLET_STOP_HOOK:-on}; docker: $docker_status. Process: gauntlet/CLAUDE.md"
else
  echo "[gauntlet] WARNING: cannot find the merge base of HEAD and $base, so the scoped checks will fail. Run 'git fetch $remote +refs/heads/$branch:refs/remotes/$remote/$branch' (after 'git fetch --unshallow $remote' in a shallow clone) or use -Pgauntlet.scope=all."
fi
exit 0

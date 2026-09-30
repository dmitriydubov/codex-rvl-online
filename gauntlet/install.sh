#!/usr/bin/env bash
# Wires the gauntlet into the project it lives in. Run from the project root:  bash gauntlet/install.sh
#
#   gauntlet/claude/settings.json  -> .claude/settings.json           (hooks + permissions; merged if one exists)
#   gauntlet/claude/agents/*.md    -> .claude/agents/                 (roles; Claude Code only looks there)
#   gauntlet/ci/gauntlet.yml       -> .github/workflows/gauntlet.yml  (CI gate for pull requests)
#   CLAUDE.md                      += @gauntlet/CLAUDE.md             (process + rules, loaded every session)
#
# Existing files: settings.json is merged in place (hooks appended, permissions united, disableAllHooks
# pinned to false); an existing role file or workflow is left as it is and reported. Nothing is written
# outside these paths. The sources under gauntlet/claude and gauntlet/ci are removed afterwards so nothing
# exists twice. Run this LAST in the install session: from the moment .claude/settings.json holds the hooks
# they are live and gauntlet/ becomes read-only for agents.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# A monorepo lists its guarded builds in gauntlet/config/gauntlet.properties (module=core,adapter); otherwise the
# build is at the root (see gauntlet/INSTALL.md, step 0).
modules=$(sed -n 's#^[[:space:]]*module[[:space:]]*=[[:space:]]*##p' gauntlet/config/gauntlet.properties 2>/dev/null | tail -1 | tr ',' ' ')
[ -f build.gradle.kts ] || [ -f build.gradle ] || [ -n "$modules" ] ||
  { echo "not a Gradle project root (and no module= in gauntlet/config/gauntlet.properties): $ROOT" >&2; exit 1; }
[ -d gauntlet/claude ] || { echo "gauntlet/claude/ is missing - already installed?" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required (hooks and the settings merge)" >&2; exit 1; }
for dir in ${modules:-.}; do
  grep -q "gauntlet/gauntlet.gradle" "$dir/build.gradle.kts" "$dir/build.gradle" 2>/dev/null ||
    echo "  !! $dir/build.gradle(.kts) does not apply gauntlet/gauntlet.gradle yet - see gauntlet/INSTALL.md step 4"
done

# --- preflight: refuse before touching anything if the existing settings cannot be merged
if [ -f .claude/settings.json ] && ! python3 -c 'import json,sys; json.load(open(".claude/settings.json"))' 2>/dev/null; then
  echo ".claude/settings.json exists but is not valid JSON - fix it first, then run the installer again" >&2
  exit 1
fi

echo "Installing the gauntlet into $ROOT"
mkdir -p .claude/agents
if [ -f .claude/settings.json ]; then
  python3 - <<'PY'
import json
with open(".claude/settings.json") as f:
    current = json.load(f)
with open("gauntlet/claude/settings.json") as f:
    ours = json.load(f)
current["disableAllHooks"] = False
hooks = current.setdefault("hooks", {})
for event, groups in ours["hooks"].items():
    existing = hooks.setdefault(event, [])
    known = {h.get("command") for g in existing for h in g.get("hooks", [])}
    for group in groups:
        if any(h.get("command") in known for h in group["hooks"]):
            continue
        existing.append(group)
perms = current.setdefault("permissions", {})
for key in ("allow", "deny"):
    merged = list(perms.get(key, []))
    merged += [rule for rule in ours["permissions"].get(key, []) if rule not in merged]
    perms[key] = merged
with open(".claude/settings.json", "w") as f:
    json.dump(current, f, indent=2, ensure_ascii=False)
    f.write("\n")
PY
  echo "  merged .claude/settings.json (gauntlet hooks and permissions added, disableAllHooks=false)"
else
  cp gauntlet/claude/settings.json .claude/settings.json
  echo "  added  .claude/settings.json"
fi
for role in gauntlet/claude/agents/*.md; do
  dst=".claude/agents/$(basename "$role")"
  if [ -e "$dst" ]; then
    echo "  kept   $dst (exists; the gauntlet version stays in git history of gauntlet/claude/agents/)"
  else
    cp "$role" "$dst"; echo "  added  $dst"
  fi
done
if [ -f gauntlet/ci/gauntlet.yml ]; then
  if [ -e .github/workflows/gauntlet.yml ]; then
    echo "  kept   .github/workflows/gauntlet.yml (exists)"
  else
    mkdir -p .github/workflows
    cp gauntlet/ci/gauntlet.yml .github/workflows/gauntlet.yml; echo "  added  .github/workflows/gauntlet.yml"
  fi
fi
if [ -f CLAUDE.md ]; then
  if grep -q "^@gauntlet/CLAUDE.md" CLAUDE.md; then
    echo "  kept   CLAUDE.md (already imports gauntlet/CLAUDE.md)"
  else
    printf '\n@gauntlet/CLAUDE.md\n' >> CLAUDE.md
    echo "  added  @gauntlet/CLAUDE.md to CLAUDE.md"
  fi
else
  printf '@gauntlet/CLAUDE.md\n' > CLAUDE.md
  echo "  added  CLAUDE.md"
fi
chmod +x gauntlet/hooks/*.py gauntlet/hooks/*.sh gauntlet/install.sh 2>/dev/null || true
rm -rf gauntlet/claude gauntlet/ci
echo "  moved  gauntlet/claude and gauntlet/ci into place (sources removed)"

cat <<'NEXT'

Done. The hooks are live from now on: gauntlet/, .claude/ and CLAUDE.md are read-only for agents.
Remaining steps (gauntlet/INSTALL.md): `git add -A`, commit, push, open the PR.
NEXT

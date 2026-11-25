#!/usr/bin/env bash
# Wires the gauntlet into the project it lives in. Run from the project root:  bash gauntlet/install.sh
#
#   gauntlet/claude/settings.json  -> .claude/settings.json           (hooks + permissions; merged if one exists)
#   gauntlet/claude/agents/*.md    -> .claude/agents/                 (roles; Claude Code only looks there)
#   gauntlet/ci/gauntlet.yml       -> .github/workflows/gauntlet.yml  (CI gate for pull requests)
#   gauntlet/ci/gauntlet-kit.yml   -> .github/workflows/gauntlet-kit.yml  (fails a pull request that changes the kit)
#   CLAUDE.md                      += @gauntlet/CLAUDE.md             (process + rules, loaded every session)
#
# Existing files: settings.json is merged in place (the gauntlet's own hooks are refreshed to this version,
# new ones appended, a second copy of one dropped, user hooks left untouched, permissions united,
# disableAllHooks pinned to false); an existing role file or workflow is left as it is; a changed managed version
# is written as *.gauntlet.new for manual merge. Nothing is written outside these paths: a symbolic link at
# .claude, .claude/settings.json or .claude/agents is refused. The sources under gauntlet/claude and gauntlet/ci
# are removed afterwards so nothing exists twice. Run this LAST in the install session: from the moment .claude/settings.json
# holds the hooks they are live and gauntlet/ becomes read-only for agents.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# A monorepo lists its guarded builds in gauntlet/config/gauntlet.properties (module=core,adapter); otherwise the
# build is at the root (see gauntlet/INSTALL.md, step 0). The list is read as the hooks and the CI workflow read
# it: `=`, `:` or blanks end the key (java.util.Properties), entries are separated by commas only - a directory
# name may hold a space - and lose their surrounding blanks and slashes. One entry per line.
modules=$(sed -En 's#^[[:space:]]*module([[:space:]]*[=:][[:space:]]*|[[:space:]]+)##p' gauntlet/config/gauntlet.properties 2>/dev/null |
  tail -1 | tr -d '\r' | tr ',' '\n' | sed 's#^[[:space:]]*##; s#[[:space:]]*$##; s#^/*##; s#/*$##; /^$/d')
[ -f build.gradle.kts ] || [ -f build.gradle ] || [ -n "$modules" ] ||
  { echo "not a Gradle project root (and no module= in gauntlet/config/gauntlet.properties): $ROOT" >&2; exit 1; }
[ -d gauntlet/claude ] || { echo "gauntlet/claude/ is missing - already installed?" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required (hooks and the settings merge)" >&2; exit 1; }
# A listed build that is not there is refused, not warned about: the hooks would watch a directory where nothing
# changes and let every stop pass without Gradle, while CI fails the same commit.
missing=
while IFS= read -r dir; do
  if [ ! -d "$dir" ]; then
    missing="$missing '$dir'"
    continue
  fi
  grep -q "gauntlet/gauntlet.gradle" "$dir/build.gradle.kts" "$dir/build.gradle" 2>/dev/null ||
    echo "  !! $dir/build.gradle(.kts) does not apply gauntlet/gauntlet.gradle yet - see gauntlet/INSTALL.md step 4"
done <<EOF
${modules:-.}
EOF
if [ -n "$missing" ]; then
  echo "module= in gauntlet/config/gauntlet.properties names no directory of this repository:$missing." >&2
  echo "List the Gradle builds the gauntlet guards, relative to the repository root, or leave module= empty for the" >&2
  echo "one build at the root (gauntlet/INSTALL.md, step 0); then run the installer again. Nothing was changed." >&2
  exit 1
fi

# --- preflight: refuse before touching anything if the existing settings cannot be merged
# The guard judges a file by its real path: behind a link, the hooks and roles would live in a file it does not
# protect (and the link may point out of the project). So a link is replaced by hand, never written through.
for path in .claude .claude/settings.json .claude/agents; do
  if [ -L "$path" ]; then
    echo "$path is a symbolic link (to $(readlink "$path")): the guard would not protect what it points to, and the" >&2
    echo "installer writes only inside the project. Replace the link with the real file or directory, then run the" >&2
    echo "installer again. Nothing was changed." >&2
    exit 1
  fi
done
if [ -e .claude/settings.json ]; then
  python3 - <<'PY' || exit 1
import json, sys

def kind(value):
    return {dict: "an object", list: "an array", str: "a string", bool: "a boolean"}.get(
        type(value), "null" if value is None else "a number")

def problem(settings):
    # Everything the merge below relies on, checked before it changes anything: a merge that failed half-way left
    # .claude/agents behind. Claude Code reads the same shape: an object, hooks an object of arrays of groups.
    if not isinstance(settings, dict):
        return f"it holds {kind(settings)}, not an object"
    hooks = settings.get("hooks", {})
    if not isinstance(hooks, dict):
        return f'"hooks" is {kind(hooks)}, not an object'
    for event, groups in hooks.items():
        where = f'"hooks"."{event}"'
        if not isinstance(groups, list):
            return f"{where} is {kind(groups)}, not an array"
        for i, group in enumerate(groups):
            if not isinstance(group, dict):
                return f"{where}[{i}] is {kind(group)}, not an object"
            entries = group.get("hooks", [])
            if not isinstance(entries, list):
                return f'{where}[{i}]."hooks" is {kind(entries)}, not an array'
            for j, entry in enumerate(entries):
                if not isinstance(entry, dict):
                    return f'{where}[{i}]."hooks"[{j}] is {kind(entry)}, not an object'
                if not isinstance(entry.get("command", ""), str):
                    return f'{where}[{i}]."hooks"[{j}]."command" is {kind(entry["command"])}, not a string'
    permissions = settings.get("permissions", {})
    if not isinstance(permissions, dict):
        return f'"permissions" is {kind(permissions)}, not an object'
    for key in ("allow", "deny"):
        rules = permissions.get(key, [])
        if not isinstance(rules, list) or not all(isinstance(rule, str) for rule in rules):
            return f'"permissions"."{key}" is not an array of strings'
    return None

try:
    with open(".claude/settings.json", encoding="utf-8") as f:
        reason = problem(json.load(f))
except ValueError as e:                 # not JSON, or not UTF-8
    reason = f"it is not valid JSON ({e})"
except OSError as e:                    # a directory, unreadable
    reason = f"it cannot be read ({e.strerror})"
if reason:
    print(f".claude/settings.json cannot be merged: {reason}. Fix it first, then run the installer again; nothing "
          f"was changed.", file=sys.stderr)
    sys.exit(1)
PY
fi

echo "Installing the gauntlet into $ROOT"
mkdir -p .claude/agents
# stop_gate runs its task in every affected module sequentially, each up to the inner --timeout in the
# command, so the outer hook timeout must outlive modules * inner + margin. Count the guarded builds.
mod_count=$(printf '%s\n' "$modules" | grep -c . || true)
case "$mod_count" in ''|*[!0-9]*) mod_count=1 ;; esac
[ "$mod_count" -ge 1 ] || mod_count=1
existed=0; [ -f .claude/settings.json ] && existed=1
GAUNTLET_MOD_COUNT="$mod_count" GAUNTLET_EXISTED="$existed" python3 - <<'PY'
import json, os, re, shlex

INTERPRETERS = {"python", "python3", "bash", "sh", "zsh", "dash", "ksh"}

VALUE_OPTIONS = ("--task", "--max-attempts", "--timeout")
# what each value may look like: a Gradle task (path) or a number - `--timeout 3300&&./audit.sh` is no timeout
VALUE_SHAPES = {"--task": r"[A-Za-z0-9_.:-]+", "--max-attempts": r"\d+", "--timeout": r"\d+"}

def stop_gate_options(args):
    # The options of a stop_gate.py call this installer understands ({"--task": "gauntletFast", ...}), else None.
    options, i = {}, 0
    while i < len(args):
        name, eq, value = args[i].partition("=")
        if args[i] == "--record-start":
            options[args[i]] = True
            i += 1
        elif eq and name in VALUE_OPTIONS and re.fullmatch(VALUE_SHAPES[name], value):
            options[name] = value
            i += 1
        elif args[i] in VALUE_OPTIONS and i + 1 < len(args) and re.fullmatch(VALUE_SHAPES[args[i]], args[i + 1]):
            options[args[i]] = args[i + 1]
            i += 2
        else:
            return None
    return options

def hook_key(name, args):
    # What a managed call is FOR. One script serves several purposes: `stop_gate.py --task gauntletFast` and a
    # user's `stop_gate.py --task integrationTest` are different hooks, and only the first is ours to refresh.
    if name in ("guard.py", "session_start.sh"):
        return name if not args else None
    if name != "stop_gate.py":
        return None
    options = stop_gate_options(args)
    if options is None:
        return None
    if options.get("--record-start"):
        return name + " --record-start"
    return name + " --task " + options.get("--task", "gauntletFast")   # stop_gate.py's own default task

def gkey(cmd):
    # Identify a gauntlet hook by the script it LAUNCHES, not by a path merely mentioned somewhere in the
    # command. The gauntlet runs `python3 "<...>/gauntlet/hooks/X.py" [flags]` / `bash "<...>/X.sh"`: the
    # script is the interpreter's first operand. A user hook like `sha256sum .../gauntlet/hooks/guard.py` or
    # `python3 -c '... guard.py ...'` only MENTIONS the path - treating it as the gauntlet's own hook would
    # overwrite the user's command on upgrade. The key is the script plus its purpose (stop_gate.py: the --task),
    # so the version-specific --timeout/--max-attempts flags can change without duplicating the hook, while a
    # user's second stop_gate.py call with another task keeps its own command.
    try:
        tokens = shlex.split(cmd or "")
    except ValueError:
        return None
    if not tokens or os.path.basename(tokens[0]) not in INTERPRETERS:
        return None
    if len(tokens) < 2 or tokens[1].startswith("-"):
        return None   # interpreter flags / inline code (-c, -m): not a plain hook-script launch
    script = re.sub(r"^(?:\./)+", "", tokens[1].replace("\\", "/"))
    m = re.fullmatch(r"(?:.*/)?gauntlet/hooks/([A-Za-z0-9_.-]+)", script)
    if not m:
        return None
    managed = "gauntlet/hooks/" + m.group(1)
    root_script = os.path.join(os.getcwd(), managed).replace(os.sep, "/")
    if script not in (managed, "$CLAUDE_PROJECT_DIR/" + managed, "${CLAUDE_PROJECT_DIR}/" + managed, root_script):
        return None
    if any(re.search(r"[\r\n;|&<>`]|\$\(", t) for t in tokens[2:]):
        return None   # a compound user command (hook && auditor, redirection) is not ours to rewrite
    return hook_key(m.group(1), tokens[2:])

def scale(event, hook, n):
    m = re.search(r"--timeout(?:\s+|=)(\d+)", hook.get("command", ""))
    inner = int(m.group(1)) if m else 0
    if event == "Stop":
        hook["timeout"] = max(1800, n * inner + 100)
    elif event == "SubagentStop":
        hook["timeout"] = max(7200, n * inner + 600)

n = max(1, int(os.environ.get("GAUNTLET_MOD_COUNT", "1")))
with open("gauntlet/claude/settings.json", encoding="utf-8") as f:
    ours = json.load(f)
if os.environ.get("GAUNTLET_EXISTED") == "1":
    with open(".claude/settings.json", encoding="utf-8") as f:     # as the preflight read it
        current = json.load(f)
else:
    current = {"hooks": {}, "permissions": {}}
current["disableAllHooks"] = False
hooks = current.setdefault("hooks", {})
for event, groups in ours["hooks"].items():
    existing = hooks.setdefault(event, [])
    our_by_key = {}
    for g in groups:
        for h in g.get("hooks", []):
            k = gkey(h.get("command"))
            if k:
                our_by_key[k] = (g, h)
    present = set()
    moves = []
    for g in list(existing):                # refresh the gauntlet's own hooks; user hooks untouched
        for h in list(g.get("hooks", [])):
            k = gkey(h.get("command"))
            if k in our_by_key:
                og, oh = our_by_key[k]
                h["command"] = oh["command"]
                h["type"] = oh.get("type", "command")
                for field in ("timeout", "statusMessage"):
                    if field in oh:
                        h[field] = oh[field]
                    else:
                        h.pop(field, None)
                own_group = all(gkey(hh.get("command")) in our_by_key for hh in g.get("hooks", []))
                if own_group:
                    if "matcher" in og:
                        g["matcher"] = og["matcher"]
                    else:
                        g.pop("matcher", None)
                elif g.get("matcher") != og.get("matcher"):
                    moves.append((g, h, og))
                present.add(k)
    for g, h, og in moves:
        # A broad user matcher must stay on the user's group, but must not broaden the gauntlet hook's scope.
        g["hooks"] = [hh for hh in g.get("hooks", []) if hh is not h]
        ng = {key: json.loads(json.dumps(value)) for key, value in og.items() if key != "hooks"}
        ng["hooks"] = [h]
        existing.append(ng)
    for g in groups:                        # append gauntlet groups not already present
        if any(gkey(h.get("command")) in present for h in g.get("hooks", [])):
            continue
        ng = json.loads(json.dumps(g))
        existing.append(ng)
        for h in ng.get("hooks", []):
            k = gkey(h.get("command"))
            if k:
                present.add(k)
    # A kit hook held twice (copied by hand, or once more in a user's group) runs twice: Stop would run the gate
    # twice per stop. Keep the first copy of each; a group left empty by that goes too. User hooks stay as they
    # are, repeated or not.
    seen = set()
    for g in list(existing):
        entries = g.get("hooks", [])
        kept = []
        for h in entries:
            k = gkey(h.get("command"))
            if k in our_by_key:
                if k in seen:
                    continue
                seen.add(k)
            kept.append(h)
        if len(kept) < len(entries):
            if kept:
                g["hooks"] = kept
            else:
                existing[:] = [other for other in existing if other is not g]
    for g in existing:                      # scale the outer timeout of the gauntlet's Gradle hooks by modules
        for h in g.get("hooks", []):
            if gkey(h.get("command")) in our_by_key:
                scale(event, h, n)
perms = current.setdefault("permissions", {})
for key in ("allow", "deny"):
    merged = list(perms.get(key, []))
    merged += [rule for rule in ours["permissions"].get(key, []) if rule not in merged]
    perms[key] = merged
with open(".claude/settings.json", "w", encoding="utf-8") as f:
    json.dump(current, f, indent=2, ensure_ascii=False)
    f.write("\n")
PY
if [ "$existed" = 1 ]; then
  echo "  merged .claude/settings.json (gauntlet hooks refreshed, permissions united, disableAllHooks=false)"
else
  echo "  added  .claude/settings.json"
fi
for role in gauntlet/claude/agents/*.md; do
  dst=".claude/agents/$(basename "$role")"
  if [ -e "$dst" ]; then
    if cmp -s "$role" "$dst"; then
      echo "  kept   $dst (already current)"
    else
      cp "$role" "$dst.gauntlet.new"
      echo "  review $dst.gauntlet.new (merge the managed update into $dst; the existing file is untouched)"
    fi
  else
    cp "$role" "$dst"; echo "  added  $dst"
  fi
done
for workflow in gauntlet.yml gauntlet-kit.yml; do
  src="gauntlet/ci/$workflow"
  dst=".github/workflows/$workflow"
  [ -f "$src" ] || continue
  if [ -e "$dst" ]; then
    if cmp -s "$src" "$dst"; then
      echo "  kept   $dst (already current)"
    else
      cp "$src" "$dst.gauntlet.new"
      echo "  review $dst.gauntlet.new (merge the managed update; the existing workflow is untouched)"
    fi
  else
    mkdir -p .github/workflows
    cp "$src" "$dst"; echo "  added  $dst"
  fi
done
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

Done. The hooks are live from now on: gauntlet/, .claude/, CLAUDE.md, the kit's workflows and the other agent
tools' files (AGENTS.md, .codex/, .mcp.json and more - gauntlet/README.md, section 5) are read-only for agents.
Remaining steps (gauntlet/INSTALL.md): `git add -A`, commit, push, open the PR. After the merge the human makes
the checks `gauntlet` and `gauntlet kit unchanged` required for the base branch on GitHub, with GitHub Actions as
their source (gauntlet/README.md, section 5).
NEXT

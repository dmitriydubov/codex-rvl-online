#!/usr/bin/env bash
# Initializes or updates an isolated single-vendor repository from the current rvl-online checkout.
# Usage:
#   gauntlet/adapters/sync_vendor_repos.sh <neon|codex|grok|kimi|qwen> <git-remote-url>
# Example (run from a clean main checkout of rvl-online after merging H1/H3):
#   for v in neon codex grok kimi qwen; do
#     gauntlet/adapters/sync_vendor_repos.sh "$v" "git@github.com:dmitriydubov/${v}-rvl-online.git"
#   done
set -euo pipefail

vendor="${1:?Usage: $0 <neon|codex|grok|kimi|qwen> <remote-url>}"
remote="${2:?Usage: $0 <neon|codex|grok|kimi|qwen> <remote-url>}"
src_root="$(git rev-parse --show-toplevel)"
full_sha="$(git -C "$src_root" rev-parse HEAD)"
base_sha="$(git -C "$src_root" rev-parse --short HEAD)"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/rvl-vendor-${vendor}.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

git clone --quiet "$src_root" "$tmp_dir/repo"
cd "$tmp_dir/repo"
git checkout -q -B main "$full_sha"

if [ "$vendor" != "neon" ]; then
  git rm -rf --quiet --ignore-unmatch GEMINI.md .agents
fi

case "$vendor" in
  neon)
    # GEMINI.md + .agents/ are already active on main
    ;;
  codex)
    cp -a gauntlet/adapters/codex/overlay/.codex .
    cp -a gauntlet/adapters/codex/overlay/AGENTS.md .
    ;;
  grok)
    cp -a gauntlet/adapters/grok/overlay/.grok .
    ln -sf ../../gauntlet/CLAUDE.md .grok/rules/10-gauntlet-kit.md
    ;;
  kimi)
    mkdir -p .kimi-code
    ln -sf ../gauntlet/adapters/kimi/AGENTS.md .kimi-code/AGENTS.md
    ;;
  qwen)
    cp -a gauntlet/adapters/qwen/overlay/.qwen .
    cp -a gauntlet/adapters/qwen/overlay/QWEN.md .
    ;;
  *)
    echo "Unknown vendor: $vendor (expected neon|codex|grok|kimi|qwen)" >&2
    exit 2
    ;;
esac

if [ -n "$(git status --porcelain)" ]; then
  git add -A -f
  git commit -q -m "chore: activate ${vendor} adapter on base ${base_sha}. By coordinator."
fi

git remote set-url origin "$remote"
git push --force origin HEAD:refs/heads/main
echo "[ok] ${vendor} -> ${remote} (base ${base_sha}, head $(git rev-parse --short HEAD))"

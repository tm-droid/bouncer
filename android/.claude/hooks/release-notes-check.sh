#!/usr/bin/env bash
# PreToolUse hook (Bash): block a `git commit` that changes the shipped app without touching the
# Play release notes. Every push to main uploads a bundle with whatever
# distribution/whatsnew/whatsnew-en-US says at that moment, so notes left behind ship stale.
#
# Escape hatch for commits with nothing a user would notice (refactors, tests, CI):
#   SKIP_RELEASE_NOTES=1 git commit ...
set -euo pipefail

cmd=$(jq -r '.tool_input.command // empty')
[[ "$cmd" =~ (^|[;&|[:space:]])git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+commit([[:space:]]|$) ]] || exit 0
[[ "$cmd" == *SKIP_RELEASE_NOTES=1* ]] && exit 0

# The android/ folder this hook lives in. The git root is its parent, so paths are listed relative
# to android/ (`--relative`); root-relative paths ("android/app/...") never matched the patterns below.
cd "$(dirname "$0")/../.."

# Staged plus unstaged: the command may be `git add … && git commit`, which stages after this runs.
changed=$( { git diff --cached --name-only --relative; git diff --name-only --relative; git ls-files --others --exclude-standard; } | sort -u )

grep -q '^app/src/main/' <<<"$changed" || exit 0
grep -q '^distribution/whatsnew/whatsnew-en-US$' <<<"$changed" && exit 0

cat >&2 <<'EOF'
Release notes not updated. This commit changes app/src/main/, and every push to main uploads to
Play with distribution/whatsnew/whatsnew-en-US as "What's new".

Do one of:
  - Update distribution/whatsnew/whatsnew-en-US (<=500 chars, written for users, not the diff;
    see distribution/whatsnew/README.md) and stage it in this commit. If this bumps versionName,
    move the old file to distribution/whatsnew/history/<old version>.txt first.
  - If nothing user-visible changed, re-run as: SKIP_RELEASE_NOTES=1 git commit ...
EOF
exit 2

#!/usr/bin/env bash
# .claude/hooks/guard-git.sh — PreToolUse on Bash
# Blocks force-push (any argument position) and other git history/working-tree destroyers.
set -euo pipefail

cmd="$(cat | jq -r '.tool_input.command // ""')"

# Only inspect commands that invoke git
printf '%s' "$cmd" | grep -Eq '(^|[;&|[:space:]])git[[:space:]]' || exit 0

# Force-push in any position: -f, --force, --force-with-lease
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+push\b.*(--force(-with-lease)?\b|(^|[[:space:]])-f\b)'; then
  echo "Blocked: force push rewrites shared history — open a PR instead." >&2
  exit 2
fi

# History / working-tree destroyers: reset --hard, clean -f*/--force, push --mirror
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+(reset[[:space:]]+--hard|clean[[:space:]]+(-[a-z]*f|--force)|push[[:space:]]+.*--mirror)'; then
  echo "Blocked: destructive git operation (hard reset / clean -f / mirror push)." >&2
  exit 2
fi

exit 0

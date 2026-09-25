#!/usr/bin/env bash
# .claude/hooks/guard-git.sh — PreToolUse on Bash
# Blocks force-push (any position, any branch, any refspec form) and other git
# history/working-tree destroyers. Checks each command of a chain (a && b; c | d)
# on its own, and is tolerant of options between `git` and the subcommand
# (e.g. `git -C . push --force`) and of quoted arguments.
set -euo pipefail

command -v jq >/dev/null || { echo "Blocked: this hook needs jq installed." >&2; exit 2; }

input="$(cat)"
printf '%s' "$input" | jq -e . >/dev/null 2>&1 || { echo "Blocked: could not parse tool-call JSON." >&2; exit 2; }
cmd="$(printf '%s' "$input" | jq -r '.tool_input.command // ""')"

# Drop quote marks so a quoted branch/arg cannot hide from the patterns below.
cmd="${cmd//\"/}"
cmd="${cmd//\'/}"

has() { printf '%s' "$1" | grep -Eq "$2"; }

is_git='(^|[[:space:]])git([[:space:]]|$)'
is_push='(^|[[:space:]])push([[:space:]]|$)'
is_force='(^|[[:space:]])(--force(-with-lease)?|-[[:alnum:]]*f[[:alnum:]]*|\+[^[:space:]]+)([[:space:]]|$)'
is_reset='(^|[[:space:]])reset([[:space:]]|$)'
is_hard='(^|[[:space:]])--hard([[:space:]]|$)'
is_clean='(^|[[:space:]])clean([[:space:]]|$)'
is_force_flag_or_word='(^|[[:space:]])(-[[:alnum:]]*f[[:alnum:]]*|--force)([[:space:]]|$)'
is_mirror='(^|[[:space:]])--mirror([[:space:]]|$)'

while IFS= read -r part; do
  has "$part" "$is_git" || continue

  if has "$part" "$is_push" && has "$part" "$is_force"; then
    echo "Blocked: force push rewrites shared history — open a PR instead." >&2
    exit 2
  fi

  if has "$part" "$is_push" && has "$part" "$is_mirror"; then
    echo "Blocked: mirror push can overwrite/delete remote refs — not allowed." >&2
    exit 2
  fi

  if has "$part" "$is_reset" && has "$part" "$is_hard"; then
    echo "Blocked: destructive git operation (hard reset discards uncommitted work)." >&2
    exit 2
  fi

  if has "$part" "$is_clean" && has "$part" "$is_force_flag_or_word"; then
    echo "Blocked: destructive git operation (clean -f deletes untracked files)." >&2
    exit 2
  fi
done < <(printf '%s\n' "$cmd" | tr ';&|' '\n\n\n')

exit 0

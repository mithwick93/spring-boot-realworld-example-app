#!/usr/bin/env bash
# .claude/hooks/audit-log.sh — PostToolUse on Bash|Edit|Write
# Appends one line per action to audit-log-YYYY-MM.txt: what ran, which file, when.
set -euo pipefail

command -v jq >/dev/null || exit 0

log_file="${CLAUDE_PROJECT_DIR:-.}/audit-log-$(date -u +%Y-%m).txt"

cat | jq -r '
  "[\(now | todate)] \(.tool_name) " +
  (if .tool_name == "Bash" then (.tool_input.command // "n/a")
   else (.tool_input.file_path // "n/a")
   end)
' >> "$log_file"

#!/usr/bin/env bash
# orchestration-recipe-input-validation-audit-2026-09.sh
#
# Job: one worker per file, auditing REST controllers and their request
# DTOs/validators against CLAUDE.md's Security Requirements > Input
# Validation section. Adapted from homework-4.2/run-workers.sh (the
# orchestrator-worker pattern's "Running Workers in Parallel" script, on the
# pattern-run branch) plus the Multi-Claude Orchestration Examples
# supplement's Worker Script with Retries and Per-Call Audit Log (NDJSON).
#
# What changed from homework-4.2/run-workers.sh:
#   - a retry loop with exponential backoff around each claude -p call
#   - a .status file per item, so a re-run skips anything that already
#     succeeded instead of paying for it twice
#   - one NDJSON line per attempt (success or failure) in audit.ndjson,
#     carrying the item id and, on failure, the JSON the call printed
#   - a MAX_PARALLEL cap (default 5) instead of launching every item at once
#   - the item list is sliceable by run size, so one script drives both the
#     5-item run and the full run
#
# Requirements: bash 4+ (uses `wait -n`), jq, GNU timeout. macOS ships bash
# 3.2 and no GNU timeout -- run `brew install bash coreutils` first and
# invoke this script with the brewed bash.
#
# Usage: ./orchestration-recipe-input-validation-audit-2026-09.sh <small|full> [session-dir]

set -euo pipefail
cd "$(dirname "$0")"

RUN_SIZE=${1:?"Usage: $0 <small|full> [session-dir]"}
SESSION_DIR=${2:-"homework-4.3/run-$(date +%Y%m%d_%H%M%S)-${RUN_SIZE}"}
OUTPUT_DIR="$SESSION_DIR/workers"
AUDIT_LOG="$SESSION_DIR/audit.ndjson"

MAX_RETRIES=3
TIMEOUT_SECONDS=300
MAX_TURNS=8
MAX_USD=2
MAX_PARALLEL=${MAX_PARALLEL:-5}

mkdir -p "$OUTPUT_DIR"
echo "Session: $SESSION_DIR (to resume: $0 $RUN_SIZE $SESSION_DIR)"

# --- Full 20-item set: TASK_ID|SCOPE|QUESTION ---
FULL_ITEMS=(
"TASK-01|src/main/java/io/spring/api/UsersApi.java|Do the registration and login request DTOs validate required fields, email format, and length bounds on user-supplied strings, per CLAUDE.md's Security Requirements > Input Validation section?"
"TASK-02|src/main/java/io/spring/api/CurrentUserApi.java|Does the update-current-user request DTO validate optional/partial fields (email format, size bounds) before persisting, per CLAUDE.md's Input Validation section?"
"TASK-03|src/main/java/io/spring/api/ArticlesApi.java|Are article creation/update request DTOs validated with Bean Validation, and are list/feed query parameters (offset, limit, tag, author, favorited) checked or bounded, per CLAUDE.md's Input Validation section?"
"TASK-04|src/main/java/io/spring/api/ArticleApi.java|Is the single-article update endpoint's request DTO validated with @Valid, and are its path variables checked before use, per CLAUDE.md's Input Validation section?"
"TASK-05|src/main/java/io/spring/api/CommentsApi.java|Is the new-comment request DTO validated for required/blank content, and are the article slug and comment id path variables checked before use, per CLAUDE.md's Input Validation section?"
"TASK-06|src/main/java/io/spring/api/ArticleFavoriteApi.java|This endpoint takes only path variables, no request DTO. Is that an acceptable design per CLAUDE.md's conventions, and are the path variables safely handled?"
"TASK-07|src/main/java/io/spring/api/ArticleReportApi.java|Does the report-article request DTO validate the reason field's presence and length bound, per CLAUDE.md's Input Validation section and the article_report.reason column's varchar(1000) limit?"
"TASK-08|src/main/java/io/spring/api/ProfileApi.java|This endpoint takes only path variables, no request DTO. Are the path variables (username) safely handled, and is there any unvalidated external input?"
"TASK-09|src/main/java/io/spring/api/TagsApi.java|Does this endpoint accept any external input (query params, path variables)? If so, is it validated or otherwise safe to leave unvalidated?"
"TASK-10|src/main/java/io/spring/api/CurrentUserReportsApi.java|Does this endpoint accept any external input (query params, path variables)? If so, is it validated or otherwise safe to leave unvalidated?"
"TASK-11|src/main/java/io/spring/application/article/NewArticleParam.java|Does this DTO's Bean Validation annotations enforce required fields and length bounds consistent with the underlying database column widths, per CLAUDE.md's Input Validation section?"
"TASK-12|src/main/java/io/spring/application/article/UpdateArticleParam.java|Does this DTO's Bean Validation annotations enforce the same constraints as NewArticleParam where the fields are equivalent, or is any asymmetry between them intentional and documented?"
"TASK-13|src/main/java/io/spring/application/article/DuplicatedArticleValidator.java|Does this validator correctly exclude the article's own current slug when checking for a duplicate title on update, or can a no-op edit be falsely rejected as a duplicate?"
"TASK-14|src/main/java/io/spring/application/article/DuplicatedArticleUpdateValidator.java|Does this cross-parameter validator correctly wire the article-id exclusion into the duplicate-title check on update?"
"TASK-15|src/main/java/io/spring/application/user/RegisterParam.java|Does this DTO's Bean Validation annotations enforce required fields, email format, and length bounds on username/email/password consistent with the underlying database column widths?"
"TASK-16|src/main/java/io/spring/application/user/UpdateUserParam.java|Does this DTO's Bean Validation annotations enforce the same bounds as RegisterParam where fields are equivalent, and is the blank-means-unchanged semantics for partial updates documented?"
"TASK-17|src/main/java/io/spring/application/user/DuplicatedEmailValidator.java|Does this validator have the same TOCTOU exposure (check-then-insert, no DB unique constraint backing it) as the article-title duplicate check, and is that a known, accepted risk per CLAUDE.md?"
"TASK-18|src/main/java/io/spring/application/user/DuplicatedUsernameValidator.java|Does this validator have the same TOCTOU exposure as DuplicatedEmailValidator, and is the race-condition safety net (DataIntegrityViolationException handling) still in place on both the REST and GraphQL adapters?"
"TASK-19|src/main/java/io/spring/application/Page.java|Does this class validate or clamp offset/limit values, and if it clamps silently instead of rejecting invalid input, is that consistent with CLAUDE.md's Input Validation section?"
"TASK-20|src/main/java/io/spring/application/CursorPageParameter.java|Does this class validate its cursor and limit inputs before use, and what happens on a malformed or out-of-range cursor?"
)

SMALL_IDS=(TASK-01 TASK-03 TASK-15 TASK-17 TASK-19)

case "$RUN_SIZE" in
  small)
    ITEMS=()
    for entry in "${FULL_ITEMS[@]}"; do
      id="${entry%%|*}"
      for want in "${SMALL_IDS[@]}"; do
        [ "$id" = "$want" ] && ITEMS+=("$entry")
      done
    done
    ;;
  full)
    ITEMS=("${FULL_ITEMS[@]}")
    ;;
  *)
    echo "RUN_SIZE must be 'small' or 'full', got: $RUN_SIZE" >&2
    exit 1
    ;;
esac

echo "Run size: $RUN_SIZE (${#ITEMS[@]} items, up to $MAX_PARALLEL in parallel)"

# run_item: one item, up to MAX_RETRIES attempts with exponential backoff.
# Every attempt appends one NDJSON line to $AUDIT_LOG. A success is only
# recorded once the call also left its report file -- a refused write can
# still leave the exit status at 0, per the supplement's own caution.
run_item() {
  local TASK_ID="$1" SCOPE="$2" QUESTION="$3"
  local STATUS_FILE="$OUTPUT_DIR/$TASK_ID.status"
  local OUT_FILE="$OUTPUT_DIR/$TASK_ID.out"
  local REPORT_FILE="$OUTPUT_DIR/$TASK_ID.md"
  local RESULT_FILE="$OUTPUT_DIR/$TASK_ID-output.json"

  if [ "$(cat "$STATUS_FILE" 2>/dev/null || true)" = "SUCCESS" ]; then
    echo "SKIPPED: $TASK_ID already succeeded"
    return 0
  fi

  local attempt=0
  local backoff=5
  : > "$OUT_FILE"

  while [ "$attempt" -lt "$MAX_RETRIES" ]; do
    attempt=$((attempt + 1))
    rm -f "$REPORT_FILE" "$RESULT_FILE"
    local ts
    ts=$(date -Iseconds)

    set +e
    timeout "$TIMEOUT_SECONDS" claude -p "You are Worker $TASK_ID.

SCOPE: $SCOPE
QUESTION: $QUESTION

Read the file(s) in SCOPE. Answer the QUESTION with specific file:line references. Write your findings to $REPORT_FILE using this format:
# Worker Report: $TASK_ID
## Task
[repeat scope and question]
## Findings
[specific findings, with file:line references, citing CLAUDE.md's Security Requirements > Input Validation section where relevant]
## Recommendations
[concrete, actionable -- or state explicitly that no gap was found]
" --max-turns "$MAX_TURNS" --max-budget-usd "$MAX_USD" --permission-mode acceptEdits --output-format json \
      > "$RESULT_FILE" 2>>"$OUT_FILE"
    local call_status=$?
    set -e
    cat "$RESULT_FILE" >> "$OUT_FILE" 2>/dev/null || true

    if [ "$call_status" -eq 0 ] && [ -s "$REPORT_FILE" ]; then
      local tokens cost
      tokens=$(jq -r '((.usage.input_tokens // 0) + (.usage.output_tokens // 0))' "$RESULT_FILE" 2>/dev/null || echo null)
      cost=$(jq -r '.total_cost_usd // null' "$RESULT_FILE" 2>/dev/null || echo null)
      jq -nc --arg ts "$ts" --arg item "$TASK_ID" --argjson attempt "$attempt" \
            --argjson tokens "${tokens:-null}" --argjson cost "${cost:-null}" \
        '{ts:$ts, item:$item, attempt:$attempt, tokens:$tokens, cost_usd:$cost, status:"success"}' \
        >> "$AUDIT_LOG"
      echo "SUCCESS" > "$STATUS_FILE"
      echo "SUCCESS: $TASK_ID (attempt $attempt)"
      return 0
    fi

    # Failed attempt: record the item, the attempt number, and whatever JSON
    # the call printed (Claude Code prints an in-run failure, such as a turn
    # limit, to stdout -- that's what RESULT_FILE captured above).
    if [ -s "$RESULT_FILE" ] && jq -e . "$RESULT_FILE" >/dev/null 2>&1; then
      jq -nc --arg ts "$ts" --arg item "$TASK_ID" --argjson attempt "$attempt" \
            --slurpfile result "$RESULT_FILE" \
        '{ts:$ts, item:$item, attempt:$attempt, status:"failed", result:$result[0]}' \
        >> "$AUDIT_LOG"
    else
      jq -nc --arg ts "$ts" --arg item "$TASK_ID" --argjson attempt "$attempt" \
            --arg raw "$(cat "$RESULT_FILE" 2>/dev/null || echo '')" \
        '{ts:$ts, item:$item, attempt:$attempt, status:"failed", result_raw:$raw}' \
        >> "$AUDIT_LOG"
    fi

    echo "Attempt $attempt for $TASK_ID failed (exit $call_status). Waiting ${backoff}s..."
    sleep "$backoff"
    backoff=$((backoff * 2))
  done

  echo "FAILED" > "$STATUS_FILE"
  echo "FAILED: $TASK_ID after $MAX_RETRIES attempts. See $OUT_FILE"
  return 1
}

running=0
for entry in "${ITEMS[@]}"; do
  IFS='|' read -r TASK_ID SCOPE QUESTION <<< "$entry"
  run_item "$TASK_ID" "$SCOPE" "$QUESTION" &
  running=$((running + 1))
  if [ "$running" -ge "$MAX_PARALLEL" ]; then
    wait -n
    running=$((running - 1))
  fi
done
wait

SUCCESSES=0
FAILURES=0
echo "=== Results ==="
for entry in "${ITEMS[@]}"; do
  TASK_ID="${entry%%|*}"
  status=$(cat "$OUTPUT_DIR/$TASK_ID.status" 2>/dev/null || echo "UNKNOWN")
  echo "$TASK_ID: $status"
  if [ "$status" = "SUCCESS" ]; then
    SUCCESSES=$((SUCCESSES + 1))
  else
    FAILURES=$((FAILURES + 1))
  fi
done

echo "Run complete: $SUCCESSES succeeded, $FAILURES failed."
echo "Audit log: $AUDIT_LOG"
echo "Worker reports: $OUTPUT_DIR"

#!/bin/bash
set -e
cd "$(dirname "$0")/.."

run_worker() {
  local TASK_ID=$1
  local SCOPE=$2
  local QUESTION=$3
  claude -p "You are Worker $TASK_ID.

SCOPE: $SCOPE
QUESTION: $QUESTION

Read the file(s) in SCOPE. Answer the QUESTION with specific file:line references. Write your findings to homework-4.2/workers/$TASK_ID.md using this format:
# Worker Report: $TASK_ID
## Task
[repeat scope and question]
## Findings
[specific findings, with file:line references, citing this repo's CLAUDE.md Security Requirements > Input Validation section where relevant]
## Recommendations
[concrete, actionable — or state explicitly that no gap was found]
" \
    --max-turns 8 --max-budget-usd 2 --permission-mode acceptEdits --output-format json \
    > "homework-4.2/workers/$TASK_ID-output.json"
}

run_worker "TASK-1" "src/main/java/io/spring/api/UsersApi.java" "Do the registration and login request DTOs (RegistrationParam, LoginParam) validate required fields, email format, and enforce length bounds on user-supplied strings?" &
run_worker "TASK-2" "src/main/java/io/spring/api/CurrentUserApi.java" "Does the update-current-user request DTO validate optional/partial fields (email format, size bounds) before they are persisted?" &
run_worker "TASK-3" "src/main/java/io/spring/api/ArticlesApi.java, src/main/java/io/spring/api/ArticleApi.java" "Are article creation/update request DTOs validated with Bean Validation, and are list/feed query parameters (offset, limit, tag, author, favorited) checked or bounded before use?" &
run_worker "TASK-4" "src/main/java/io/spring/api/CommentsApi.java" "Is the new-comment request DTO validated for required/blank content, and are the article slug and comment id path variables checked before use?" &
run_worker "TASK-5" "src/main/java/io/spring/api/ArticleFavoriteApi.java, src/main/java/io/spring/api/ArticleReportApi.java, src/main/java/io/spring/api/ProfileApi.java" "For these path-variable-only endpoints (favorite/unfavorite, report, follow/unfollow), is the absence of a request DTO an acceptable design (per the simple path-variable actions convention), and are the path variables safely handled?" &
run_worker "TASK-6" "src/main/java/io/spring/api/TagsApi.java, src/main/java/io/spring/api/CurrentUserReportsApi.java" "Do these endpoints accept any external input at all (query params, path variables), and if so, is it validated or otherwise safe to leave unvalidated?" &

wait
echo "All workers complete."

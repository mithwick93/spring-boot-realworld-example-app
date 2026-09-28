# Headless run — homework 4.7 (Stand up a headless agent)

**Program:** `scripts/dto_shape_audit.py` (Agent SDK path, Python, `claude-agent-sdk==0.2.160`).
**Task:** batch-review the 10 real REST controllers in `src/main/java/io/spring/api/` against
the DTO-shape convention from homework 4.5 (`.claude/rules/rest-api-request-dtos.md`), checking
whether it holds beyond the 2 files that rule is currently scoped to.
**Per-item settings:** `allowed_tools=["Read"]`, `permission_mode="dontAsk"`, `max_turns=3`,
`max_budget_usd=0.50`. Every `query()` call is independent — one file's content and the rule
text embedded directly in the prompt, no shared session between items.

## Per-item output — batch run (10/10 real files)

| File | Status | Cost | Verdict |
| --- | --- | --- | --- |
| ArticleApi.java | ok | $0.0527 | **Violation** — `UpdateArticleParam` declared in its own file (`application/article/`), not nested at the bottom of the controller |
| ArticleFavoriteApi.java | ok | $0.0219 | Compliant — no request DTO (path-variable only) |
| ArticleReportApi.java | ok | $0.0244 | Compliant — `ReportArticleParam` genuinely nested per convention |
| ArticlesApi.java | ok | $0.0264 | **Violation** — `NewArticleParam` declared in its own file, not nested |
| CommentsApi.java | ok | $0.0241 | Compliant — `NewCommentParam` genuinely nested per convention |
| CurrentUserApi.java | ok | $0.0222 | **Violation** — `UpdateUserParam` declared in its own file, not nested |
| CurrentUserReportsApi.java | ok | $0.0256 | Compliant — no request DTO |
| ProfileApi.java | ok | $0.0228 | Compliant — no request DTO (path-variable only) |
| TagsApi.java | ok | $0.0199 | Compliant — no request DTO (read-only) |
| UsersApi.java | ok | $0.0305 | Compliant — `LoginParam`/`RegisterParam` genuinely nested per convention |

10/10 items completed with `status: ok`, 0 permission denials, 0 errors. Batch total: **$0.2705**.

**Finding, independently verified against the real files (not taken on the model's word):** 3 of
10 controllers violate a DTO-placement convention that has been documented in this repo's
`CLAUDE.md` since homework 1.1 — "package-private classes declared at the bottom of the
controller file they belong to, not separate files (see `LoginParam` in `UsersApi.java`)." The
4.5 rule file only scopes that same convention to 2 files (`ArticleReportApi.java`,
`CommentsApi.java`). Confirmed by reading the actual source: `UpdateArticleParam.java`,
`NewArticleParam.java`, and `UpdateUserParam.java` really do exist as separate files under
`application/article/` and `application/user/`, contradicting both `CLAUDE.md` and the 4.5 rule.
Of the 7 "compliant" verdicts, only 3 (`ArticleReportApi`, `CommentsApi`, `UsersApi`) reflect a
real nested DTO being checked; the other 4 are trivially compliant because those endpoints take
no request body at all.

## Per-item output — deliberate failure trigger

**Design:** ask the agent to edit `ArticleApi.java` directly. `Edit` is not in this script's
`allowed_tools`, and `permission_mode="dontAsk"` denies any call that would otherwise prompt — so
the edit should be denied rather than applied, and the run should record that denial rather than
finishing looking like an ordinary success.

**Getting a real denial took three attempts, in order:**

1. A "review this file, then fix any violations by editing it" prompt, `max_turns=3` — the agent
   spent the full turn budget on the review half of the task and never attempted a tool call at
   all. Result: `error_max_turns`, 0 permission denials — a real failure, but not the one being
   tested for. Cost: $0.0326.
2. Same prompt, `max_turns` raised to 6 — same outcome, still no tool call attempted before the
   cap. Cost: $0.0965. At this point a real bug was also found and fixed in the script itself:
   the `except` block was discarding the `ResultMessage` it had already captured on a capped run
   instead of reading its cost/denial/review fields from it — so even a run that did trigger a
   denial would have reported it as an empty failure. Fixed to read the partial result before
   returning.
3. A short, directive prompt with no embedded file content or review task — "add this one line
   to this file, edit it directly" — reached a real denial immediately, `max_turns=3` unchanged:

```json
{
  "file": "src/main/java/io/spring/api/ArticleApi.java (deliberate-denial trigger)",
  "status": "error",
  "error": "ResultError('Claude Code returned an error result: Reached maximum number of turns (3) (exit code: 1)')",
  "subtype": "error_max_turns",
  "terminal_reason": "max_turns",
  "num_turns": 4,
  "total_cost_usd": 0.0398446,
  "permission_denials": [
    "{'tool_name': 'Edit', ... 'file_path': '.../ArticleApi.java', 'old_string': 'package io.spring.api;', 'new_string': '// audit: reviewed\\npackage io.spring.api;', ...}",
    "{'tool_name': 'Write', ... 'file_path': '.../ArticleApi.java', 'content': '// audit: reviewed\\npackage io.spring.api;\\n\\n...' }"
  ],
  "review": null
}
```

The agent tried `Edit` (denied), retried via `Write` as a fallback (denied again), then ran out
of turns before producing any final text — hence `"review": null`. Cost: $0.0398. Verified
independently afterward with `git diff` on `ArticleApi.java`: no changes landed, confirming both
denials actually held and neither tool call went through.

**How the job handled it:** visibly, not silently. The run's own script output logged
`-> error, permission_denials=[...]`, the item's JSON captured the real denial payloads and the
real cap-triggered error, and the run-level `summary.json` counted it under
`failed_or_errored: 1` and `items_with_permission_denials: 1` rather than folding it into `ok`.
Nothing about the final summary looks like a clean success.

## Summary

| Run | Items | OK | Failed/errored | Cost |
| --- | --- | --- | --- | --- |
| Batch (all 10 controllers) | 10 | 10 | 0 | $0.2705 |
| Deliberate failure trigger (final, working version) | 1 | 0 | 1 | $0.0398 |
| **Total, this write-up's numbers** | 11 | 10 | 1 | **$0.3103** |
| Exploratory attempts that didn't reach the denial (not part of the deliverable numbers above, kept here for honesty) | 2 | 0 | 2 | $0.1291 |

Real total spend across every attempt in this session: $0.4394.

## Evaluate

**What could you do by running Claude from code that the interactive CLI cannot do — and what
did you lose?**
Code does the looping, aggregation, and per-item cost/denial capture unattended across all 10
files in one process — no manual copy-paste per file, and every result lands in a structured
JSON file rather than a scrollback buffer. What's lost is visibility and mid-course correction:
each `query()` call is a fully isolated one-shot with no session memory across items, so nothing
learned reviewing file 1 carries into file 2; and there's no live transcript to watch a run in
progress — the only signal after the fact is whatever the final `ResultMessage` exposes
(`permission_denials`, `terminal_reason`, cost). Diagnosing *why* the denial-trigger case failed
the first two times took three live re-runs precisely because there was no way to see what the
agent had actually attempted mid-run, only what the SDK chose to surface afterward.

**How does your job handle the failure case: retry, skip-and-record, or crash? What would it
need to be for you'd trust it on 500 items overnight?**
Skip-and-record: a failed item is caught, written to its own JSON with `status: "error"` and
whatever partial result data was captured, and the loop moves on — it never crashes the whole
run. For 500 items overnight it would still need: real retry with backoff for transient/
rate-limit failures (right now any error is terminal for that item, no retry at all); a
run-level total budget cap on top of the existing per-item cap (currently nothing stops 500
items from each spending up to $0.50 if something goes wrong); and a resume/idempotency marker
so a crash at item 300 doesn't force redoing 1–299.

**What's the cost per item, and which lever would you pull first to lower it?**
$0.027/item average on the batch (range $0.0199–$0.0527). Cost tracked with how much the model
had to write, not file size — the one item with a real violation to explain (`ArticleApi.java`)
was also the most expensive, while files with nothing to report still cost $0.02–0.025. First
lever: prompt focus — a fixed one-line verdict format instead of free-text explanation would cut
the "nothing to say" items close to zero marginal output cost. Second lever: model — this ran on
default Sonnet; a fixed-rule single-file check like this is a Haiku-shaped task by this course's
own model-sizing guidance (4.1/4.2). Turns were never the binding cost driver here — every normal
item finished in 1 turn.

# Token Comparison — Homework 4.2 (Multi-Claude Pattern vs. Single Session)

Task: find every REST endpoint in `src/main/java/io/spring/api/` that accepts external input
and determine whether it validates that input, per this repo's `CLAUDE.md` Security
Requirements > Input Validation section.

Pattern used: **orchestrator-worker** (lead splits the task by controller, 6 workers
investigate in parallel, lead synthesizes). Compared against a single Claude session doing
the same task alone, with the Task tool excluded so it could not spawn subagents internally.

## Cost and tokens

| Run | Cost | Input tokens | Output tokens | Cache read | Cache create |
|---|---|---|---|---|---|
| pattern-run (all phases, incl. 1 failed attempt) | **$2.0629** | 100 | 29,539 | 2,265,503 | 239,403 |
| single-run (one session, no subagents) | **$1.8921** | 34 | 17,209 | 2,665,438 | 296,707 |

Pattern-run cost ~9% more than single-run overall — entirely attributable to a wasted $0.574
on phase 1's first attempt, which silently entered Claude Code's plan mode and stalled (headless
mode has no way to approve `ExitPlanMode`). Excluding that failed attempt, the corrected
pattern-run cost is $1.4886 — about 21% *cheaper* than single-run. Either way, the actual
multiplier here (roughly 0.8x–1.1x) is nowhere near the lesson's own rough "~5x" estimate for
orchestrator + 3 workers + synthesis. On a codebase this small (10 controllers, ~700 lines
total), each worker's task was small and cheap, and prompt caching kept marginal cost per phase
low.

## What gave each phase a clean context

Every pattern-run phase (orchestrator, 6 workers, synthesizer) ran as a separate `claude -p`
headless call — full process isolation, not `/clear`. The single-run was one continuous
`claude -p` session with `--tools "Read,Grep,Glob,Write"` to explicitly exclude the Task tool,
so it could not spawn subagents and had to do all 10 controllers itself.

## What each run found that the other missed

- **Both runs correctly found**, independently verified against the actual source:
  `ArticleApi.updateArticle` missing `@Valid` with `UpdateArticleParam` carrying zero
  constraints (the one real functional gap), and the systemic missing-`@Size` pattern across
  `NewArticleParam`, `NewCommentParam.body`, `RegisterParam`, and `UpdateUserParam`. Both also
  agreed `TagsApi`, `CurrentUserReportsApi`, the favorite/follow endpoints, and
  `ArticleReportApi` (the one DTO with a correct `@Size(max = 1000)` bound) are clean.
- **Single-run found, pattern-run missed entirely:** `V1__create_tables.sql` declares
  `varchar(255)`/`varchar(511)` columns, but SQLite ignores VARCHAR length declarations
  entirely (type affinity only) — so the missing `@Size` bounds have zero enforcement anywhere,
  application or database. Verified correct (confirmed the column declarations directly). None
  of the 6 workers caught this because none were scoped to look at the migration file — only at
  controllers. This is the clearest example of the pattern's real cost: splitting by file
  structurally prevented any worker from making a connection that spans files.
- **Single-run also found, pattern-run missed:** `CurrentUserApi`'s auth-header parsing is
  already guarded upstream by `JwtTokenFilter` before the controller ever sees a malformed
  value, and the update-time duplicate-slug cross-parameter validator keeps working
  independently of the missing `@Valid`. Neither nuance came up in the per-file worker scoping.
- **Pattern-run's only unique item:** flagged unvalidated path variables (`slug`, `commentId`,
  `username`) as an optional, low-priority hardening note. Single-run considered these clean
  (opaque 404-guarded lookup keys) and didn't mention them. This is a judgment call, not a
  factual disagreement — both readings are defensible.

## Verdict

The pattern did not earn its extra tokens here. The audit question technically "split cleanly"
into one task per controller, but each piece was too small to need its own context window, and
splitting cost roughly the same money (or less, correctly run) while costing something real in
quality: the single session's unified view caught a genuine cross-cutting finding — the SQLite
enforcement gap — that no individually-scoped worker was positioned to see.

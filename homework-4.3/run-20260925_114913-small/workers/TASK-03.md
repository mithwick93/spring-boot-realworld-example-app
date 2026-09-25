# Worker Report: TASK-03

## Task
SCOPE: `src/main/java/io/spring/api/ArticlesApi.java`
QUESTION: Are article creation/update request DTOs validated with Bean Validation, and are list/feed query parameters (offset, limit, tag, author, favorited) checked or bounded, per CLAUDE.md's Input Validation section?

## Findings

**Note on scope:** `ArticlesApi.java` only contains `createArticle` (POST `/articles`), `getFeed` (GET `/articles/feed`), and `getArticles` (GET `/articles`). There is no update endpoint in this file — article update lives in `ArticleApi.java` (singular, out of scope for this task), so the "update" half of the question cannot be answered from this file alone.

**Creation DTO — validated, but incompletely:**
- `ArticlesApi.java:33-34` — `createArticle` is annotated `@Valid @RequestBody NewArticleParam newArticleParam`, so Bean Validation runs on the incoming body, consistent with CLAUDE.md's "Validate format and required fields via Bean Validation" convention.
- `NewArticleParam.java:17,21,24` — `title`, `description`, and `body` each carry `@NotBlank(message = "can't be empty")`, and `title` additionally has `@DuplicatedArticleConstraint` (`NewArticleParam.java:18`).
- Gap: **none of `title`, `description`, `body` have an `@Size` upper bound**, and `tagList` (`NewArticleParam.java:27`) has no validation annotation at all (no `@NotNull`, no `@Size` on the list, and no constraint on individual tag strings). CLAUDE.md's Input Validation section states "add `@Size` bounds on any new user-supplied string field" — these fields don't have one, so unbounded-length title/description/body/tag strings can be persisted.

**List/feed query parameters — offset/limit bounded, tag/author/favorited unchecked:**
- `ArticlesApi.java:46-47` (`getFeed`) and `ArticlesApi.java:54-55` (`getArticles`) bound `offset` with `@Min(0)` and `limit` with `@Min(1) @Max(100)`, enforced at the class level via `@Validated` (`ArticlesApi.java:27`). This satisfies input validation for the pagination parameters.
- `ArticlesApi.java:56-58` — `tag`, `favoritedBy` (`favorited` query param), and `author` are plain `@RequestParam(required = false) String` with **no validation annotation whatsoever** — no `@Size`, no pattern/format constraint, not even a null-check needed since they're optional. Per CLAUDE.md, "Treat ALL external input as untrusted" and "add `@Size` bounds on any new user-supplied string field" — these three params are unbounded free-text strings passed straight into `articleQueryService.findRecentArticles(...)`.
  - This is lower risk than it looks because CLAUDE.md's Database Access rule ("Use MyBatis parameter binding `#{param}` only") is a downstream mitigation for injection — but it doesn't cover length/resource-consumption abuse (e.g., extremely long `tag`/`author`/`favorited` values), which `@Size` bounds are meant to catch per the Input Validation section specifically.

## Recommendations
1. Add `@Size(max = <n>)` bounds to `NewArticleParam.title`, `.description`, and `.body`, and add `@Size(max = <n>)` (list size) plus a per-element constraint (e.g. `@Size` via a wrapping validator, or switch to validated tag value objects) for `tagList` — currently fully unbounded.
2. Add `@Size(max = <n>)` to the `tag`, `favoritedBy`, and `author` `@RequestParam` strings in `getArticles` (`ArticlesApi.java:56-58`) so oversized query values are rejected with a 400 instead of flowing unchecked into the query layer.
3. Confirm whether `ArticleApi.java` (the update endpoint, out of this task's scope) has the same gaps — worth a follow-up check since the question explicitly asked about "creation/update" DTOs together.

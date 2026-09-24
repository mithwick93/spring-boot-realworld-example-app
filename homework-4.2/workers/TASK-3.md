# Worker Report: TASK-3

## Task
Scope: `src/main/java/io/spring/api/ArticlesApi.java`, `src/main/java/io/spring/api/ArticleApi.java`

Question: Are article creation/update request DTOs validated with Bean Validation, and are list/feed query parameters (offset, limit, tag, author, favorited) checked or bounded before use?

## Findings

### Creation — validated, but body fields are unbounded
- `ArticlesApi.java:34` — `createArticle` uses `@Valid @RequestBody NewArticleParam newArticleParam`, so Bean Validation runs.
- `NewArticleParam.java:17,21,24` — `title`, `description`, `body` are each annotated `@NotBlank`. `title` additionally carries `@DuplicatedArticleConstraint` (`NewArticleParam.java:18`).
- `NewArticleParam.java:27` — `tagList` (`List<String>`) has no validation annotation at all — no `@NotNull`, no size/element constraints, no bound on the number of tags or the length of each tag string.
- None of `title`, `description`, `body` carry a `@Size` upper bound. Per CLAUDE.md Input Validation: *"add `@Size` bounds on any new user-supplied string field"* — these fields accept arbitrarily large strings (unbounded body/description length, no cap on article title length), which is a gap relative to that requirement.

### Update — NOT validated at all
- `ArticleApi.java:47` — `updateArticle` takes `@RequestBody UpdateArticleParam updateArticleParam` **without `@Valid`**, and the class `ArticleApi` (unlike `ArticlesApi`) is not annotated `@Validated` either.
- `UpdateArticleParam.java:12-17` — the DTO itself has zero Bean Validation annotations on `title`, `body`, `description` (they just default to `""`).
- Net effect: a `PUT /articles/{slug}` request can submit an empty-string title/body/description (silently accepted, since fields default to `""` and there's no `@NotBlank`), or arbitrarily large strings, and none of it is rejected before reaching `articleCommandService.updateArticle`. This directly contradicts CLAUDE.md Input Validation: *"Treat ALL external input as untrusted... Reject invalid input; do not attempt to sanitize and continue."* This is the clearest gap in scope.

### List/feed query parameters
- `ArticlesApi.java:27` — class is annotated `@Validated`, which is required for method-parameter-level constraints (`@Min`/`@Max` on `@RequestParam`) to be enforced.
- `ArticlesApi.java:46-47` (feed) and `:54-55` (getArticles) — `offset` is bounded with `@Min(0)`; `limit` is bounded with `@Min(1) @Max(100)`. These are checked before use in `new Page(offset, limit)`.
- `ArticlesApi.java:56-58` — `tag`, `favoritedBy` (bound to `favorited`), and `author` are plain `@RequestParam(required = false) String` with **no validation annotation at all** — no `@Size` bound, no pattern/format check. They flow straight into `articleQueryService.findRecentArticles(tag, author, favoritedBy, ...)` unchecked. Per CLAUDE.md: MyBatis parameter binding (`#{param}`) is used downstream so this is not a SQL-injection risk by itself, but it is still unvalidated/unbounded external input (e.g. no cap on string length), which the Input Validation section calls out as a requirement for any user-supplied string field.

## Recommendations
1. Add `@Valid` to `ArticleApi.updateArticle`'s `@RequestBody UpdateArticleParam` parameter, and add `@NotBlank` (or an appropriate "if present, not blank" semantics matching partial-update behavior) plus `@Size` bounds to `UpdateArticleParam.title/body/description`. This is the highest-priority gap — update currently accepts anything, including empty strings, with no validation path at all.
2. Add `@Size(max = ...)` bounds to `NewArticleParam.title`, `description`, and `body` to satisfy CLAUDE.md's "@Size bounds on any new user-supplied string field" guidance, and add a size/element-length constraint to `tagList`.
3. Add `@Size(max = ...)` (or a pattern constraint) to the `tag`, `favorited`, and `author` query parameters in `ArticlesApi.getArticles` to bound their length before they reach the query layer — currently only `offset`/`limit` are bounded, not the string filters.

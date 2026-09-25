# Worker Report: TASK-03

## Task
SCOPE: src/main/java/io/spring/api/ArticlesApi.java
QUESTION: Are article creation/update request DTOs validated with Bean Validation, and are list/feed query parameters (offset, limit, tag, author, favorited) checked or bounded, per CLAUDE.md's Input Validation section?

## Findings

- **Creation DTO validation** — `createArticle` (ArticlesApi.java:32-42) validates its body: the parameter is annotated `@Valid @RequestBody NewArticleParam newArticleParam` (ArticlesApi.java:34). This follows CLAUDE.md's Input Validation guidance ("Validate format and required fields via Bean Validation"), delegating field-level rules (e.g. `@NotBlank`/`@Size`) to `NewArticleParam` itself — that DTO's field annotations are out of scope for this file and were not inspected here.
- **No update endpoint in this file** — `ArticlesApi.java` only exposes `POST /articles` (create), `GET /articles/feed`, and `GET /articles` (list). There is no `PUT`/update mapping in this class, so the "article update" half of the question cannot be answered from this file — article update (if it exists) must live in a different controller (e.g. an `ArticleApi`-style class handling `/articles/{slug}`), which is outside this worker's scope.
- **`getFeed` pagination is bounded** (ArticlesApi.java:44-50): `offset` has `@Min(0)` (line 46) and `limit` has `@Min(1) @Max(100)` (line 47). Both are `@RequestParam` primitives validated via method-level Bean Validation, enabled by the class-level `@Validated` annotation (ArticlesApi.java:27). This satisfies CLAUDE.md's directive to bound any new user-supplied field/param.
- **`getArticles` pagination is bounded identically** (ArticlesApi.java:52-63): `offset` (`@Min(0)`, line 54) and `limit` (`@Min(1) @Max(100)`, line 55) carry the same constraints as the feed endpoint.
- **Gap — `tag`, `favorited`, `author` query params are entirely unvalidated** (ArticlesApi.java:56-58): all three are declared as plain `@RequestParam(..., required = false) String` with no `@Size`, no format constraint, and no bound on length. CLAUDE.md's Input Validation section states "add `@Size` bounds on any new user-supplied string field" — these three params have none. They are passed straight into `articleQueryService.findRecentArticles(tag, author, favoritedBy, ...)` (line 61-62), so any length/format enforcement (if it exists at all) happens further downstream in the query service / MyBatis layer, not at the API boundary as CLAUDE.md prescribes.

## Recommendations

1. Add an upper-bound `@Size(max = N)` constraint (matching whatever bound `username`/`tag` values use elsewhere in the schema, e.g. the user's `username` or `Tag.name` column length) to the `tag`, `favorited`, and `author` `@RequestParam` declarations in `getArticles` (ArticlesApi.java:56-58). Since `@Validated` is already present on the class (line 27), adding the annotation is sufficient to get method-level validation for these params, consistent with how `offset`/`limit` are already handled.
2. Locate the article-update endpoint (likely in a separate `ArticleApi.java`/slug-scoped controller) and verify separately that its update DTO carries `@Valid` and appropriate field constraints — this file provides no evidence either way for "update" validation since no update mapping exists here.
3. No gap found for `createArticle`'s use of `@Valid`, or for `offset`/`limit` bounding on either list endpoint — these already meet CLAUDE.md's Input Validation requirements.

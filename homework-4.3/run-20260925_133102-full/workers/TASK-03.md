# Worker Report: TASK-03

## Task
SCOPE: `src/main/java/io/spring/api/ArticlesApi.java`
QUESTION: Are article creation/update request DTOs validated with Bean Validation, and are list/feed query parameters (offset, limit, tag, author, favorited) checked or bounded, per CLAUDE.md's Input Validation section?

## Findings

**Article creation DTO is Bean-Validation-annotated.**
- `ArticlesApi.java:33-34` — `createArticle` takes `@Valid @RequestBody NewArticleParam newArticleParam`, so validation is actually triggered.
- `NewArticleParam.java:17,21,24` (`src/main/java/io/spring/application/article/NewArticleParam.java`) — `title`, `description`, and `body` are each annotated `@NotBlank(message = "can't be empty")`, satisfying CLAUDE.md's "validate format and required fields via Bean Validation" requirement for required-field presence.
- `NewArticleParam.java:27` — `tagList` (`List<String>`) has **no** validation annotation at all: no `@NotNull`, no `@Size` cap on list length, and no per-element constraint on tag string length/content.
- `NewArticleParam.java:17,21,24` — none of `title`/`description`/`body` has an `@Size` upper bound. CLAUDE.md's Input Validation section says to "add `@Size` bounds on any new user-supplied string field," and these are unbounded user-supplied strings (an attacker/careless client could submit an arbitrarily large `body`, `title`, or `description`).
- **Article update is out of scope for this file.** `ArticlesApi.java` only exposes `POST /articles` (create) and the two `GET` list/feed endpoints — there is no update endpoint here. Update lives in `ArticleApi.java` (not in this worker's scope), so no finding is made about the update DTO from this file alone.

**List/feed query parameters: `offset`/`limit` are bounded; `tag`/`author`/`favorited` are not validated at all.**
- `ArticlesApi.java:46-47` (`getFeed`) and `:54-55` (`getArticles`) — `offset` is annotated `@Min(0)`, and `limit` is annotated `@Min(1) @Max(100)`, with the class-level `@Validated` (`ArticlesApi.java:27`) enabling method-parameter constraint validation. This satisfies CLAUDE.md's requirement to bound/validate external input for these two numeric params.
- `ArticlesApi.java:56-58` — `tag`, `favoritedBy` (bound to `favorited`), and `author` are plain `@RequestParam(required = false) String` with **no validation annotation whatsoever** — no `@Size` length cap, no format/pattern constraint, not even a blank/whitespace check. Per CLAUDE.md: "Validate format and required fields via Bean Validation... add `@Size` bounds on any new user-supplied string field" and "Treat ALL external input as untrusted (request bodies, query params...)." These three query params are untrusted external input reaching the persistence layer (via `ArticleQueryService.findRecentArticles`, `ArticlesApi.java:60-62`) with no length/format bound before use.
  - Note: CLAUDE.md's Database Access section requires MyBatis `#{param}` binding (not string concatenation) to prevent injection — that mitigates SQL injection risk specifically, but does not address the separate Input Validation requirement to bound/validate the values themselves (e.g., unbounded-length `tag`/`author`/`favorited` strings could still be used for resource-exhaustion-style abuse or downstream mis-handling, and CLAUDE.md's rule applies independent of injection risk).

## Recommendations
1. Add `@Size(max = <reasonable-limit>)` to `NewArticleParam.title`, `.description`, and `.body` (`NewArticleParam.java:17,21,24`) to cap payload size, per CLAUDE.md's explicit `@Size` guidance for user-supplied string fields.
2. Add a `@Size(max = <N>)` constraint on `NewArticleParam.tagList` (list size) and consider a per-element `@Size`/`@Pattern` via `@Valid` + a wrapping constraint, or at minimum document/enforce an application-level cap before it reaches persistence (`NewArticleParam.java:27`).
3. Add `@Size(max = <reasonable-limit>)` to the `tag`, `favorited`, and `author` `@RequestParam` String parameters in both `getFeed`/`getArticles` (`ArticlesApi.java:56-58`) so arbitrary-length query strings are rejected consistently with the existing `offset`/`limit` bounding pattern already used in the same methods.
4. When reviewing the update path, check the sibling file `ArticleApi.java` (out of this task's scope) for the same class of gaps, since CLAUDE.md's question groups creation and update together but this file only contains creation.

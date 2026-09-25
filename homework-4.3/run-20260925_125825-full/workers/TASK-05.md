# Worker Report: TASK-05

## Task
Scope: `src/main/java/io/spring/api/CommentsApi.java`
Question: Is the new-comment request DTO validated for required/blank content, and are the article slug and comment id path variables checked before use, per CLAUDE.md's Input Validation section?

## Findings

**New-comment body content — validated.**
- `NewCommentParam.body` is annotated `@NotBlank(message = "can't be empty")` (`CommentsApi.java:100-101`).
- The `createComment` handler binds it via `@Valid @RequestBody NewCommentParam newCommentParam` (`CommentsApi.java:44`), so Bean Validation runs before the field is used to construct the `Comment` entity (`CommentsApi.java:47`). This satisfies CLAUDE.md's Input Validation requirement to "Validate format and required fields via Bean Validation" for user-supplied request bodies. No `@Size` upper bound is set on `body`, which the CLAUDE.md guidance ("add `@Size` bounds on any new user-supplied string field") would call for, though this predates any change in this task's scope and mirrors the rest of the codebase's DTOs (no existing bound to follow as precedent in this file).

**Path variables `slug` and `id` (commentId) — not format-validated, but existence-checked before use.**
- Neither `@PathVariable("slug") String slug` (`CommentsApi.java:42`, `55`, `69`) nor `@PathVariable("id") String commentId` (`CommentsApi.java:70`) carries any Bean Validation annotation (`@NotBlank`, `@Pattern`, `@Size`, etc.) — there is no explicit rejection of blank/malformed values at the controller boundary.
- However, both are immediately used only as lookup keys against repositories, and every path fails closed if the lookup misses:
  - `createComment`: `articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new)` (`CommentsApi.java:45-46`).
  - `getComments`: same pattern (`CommentsApi.java:56-57`).
  - `deleteComment`: `articleRepository.findBySlug(slug).orElseThrow(...)` (`CommentsApi.java:72-73`) followed by `commentRepository.findById(article.getId(), commentId)...orElseThrow(ResourceNotFoundException::new)` (`CommentsApi.java:74-84`).
- So a blank, malformed, or nonexistent `slug`/`id` cannot reach any write or authorization logic unvalidated — it simply fails to resolve to an entity and short-circuits to a 404 (`ResourceNotFoundException`), before `commentRepository.remove(comment)` or any other mutation runs. This is the same "simple path-variable, no DTO" pattern CLAUDE.md's Conventions section documents for `ProfileApi.follow`/`unfollow`, which likewise skip explicit annotations and rely on repository-lookup-or-404.
- Values are passed to MyBatis via parameter binding (not string concatenation), so this also satisfies the Database Access requirement to use `#{param}` binding only — no injection risk from the unvalidated path variables.

## Recommendations
- No functional gap found: the comment body is validated per CLAUDE.md's Bean Validation requirement, and both path variables are checked before use via repository lookups that throw `ResourceNotFoundException` on miss, consistent with this repo's documented convention for simple path-variable endpoints.
- Optional hardening (not a compliance gap, just a tightening opportunity): add `@Size(max = ...)` to `NewCommentParam.body` (`CommentsApi.java:100-101`) to bound comment length, per CLAUDE.md's "add `@Size` bounds on any new user-supplied string field" guidance — currently unbounded.

# Worker Report: TASK-4

## Task
SCOPE: `src/main/java/io/spring/api/CommentsApi.java`
QUESTION: Is the new-comment request DTO validated for required/blank content, and are the article slug and comment id path variables checked before use?

## Findings

**Comment body content — validated.**
- `NewCommentParam.body` carries `@NotBlank(message = "can't be empty")` (`CommentsApi.java:100-101`).
- `createComment` binds it via `@Valid @RequestBody NewCommentParam newCommentParam` (`CommentsApi.java:44`), so Bean Validation runs before the handler body executes and a blank/missing `body` is rejected with a 400 before `new Comment(...)` is constructed. This matches CLAUDE.md's Input Validation guidance to "Validate format and required fields via Bean Validation."

**Article slug path variable — existence checked, but not format/size validated.**
- In all three handlers (`createComment` line 42/45-46, `getComments` line 55-57, `deleteComment` line 69/72-73), `slug` is passed straight into `articleRepository.findBySlug(slug)`, and a missing article throws `ResourceNotFoundException` via `.orElseThrow(...)`. So existence is checked before the slug is used to construct a `Comment` or perform further lookups.
- However, `slug` itself is a raw `@PathVariable String` with no `@Size`/`@Pattern` constraint. CLAUDE.md's Input Validation section says to treat all external input as untrusted and "add `@Size` bounds on any new user-supplied string field" — that bound is absent here. This is low severity since `findBySlug` goes through MyBatis parameter binding (`#{}`, no injection risk per the Database Access rule), but an arbitrarily long/malformed slug still reaches the persistence layer unvalidated.

**Comment id path variable — existence checked at lookup time, not validated for format.**
- `deleteComment` (`CommentsApi.java:70`) takes `commentId` as a raw `String` with no bounds/format constraint, then uses it in `commentRepository.findById(article.getId(), commentId)` (`CommentsApi.java:75`). If no matching comment exists, `.orElseThrow(ResourceNotFoundException::new)` (line 84) fires, so existence is verified before `commentRepository.remove(comment)` (line 81) runs.
- Same gap as the slug: no `@Size`/format validation on `commentId` before it hits the repository, per CLAUDE.md's Input Validation section.

**Authorization is checked for delete, but not for the id itself.** Note (secondary, not part of the question but related): `deleteComment` correctly checks `AuthorizationService.canWriteComment(user, article, comment)` (line 78) before removal — this guards against IDOR on `commentId`, which mitigates the impact of the missing format validation above.

## Recommendations

- Add `@Size(max = <reasonable bound>)` (and optionally `@Pattern` matching the slug-generation format) to the `slug` and `id` `@PathVariable String` parameters in `CommentsApi.java` (lines 42, 69-70, 55) to close the CLAUDE.md Input Validation gap on unbounded external string input. This is a minor hardening item, not a functional bug — both variables are already existence-checked against the repository (404 on miss) and the comment-id path is additionally guarded by an authorization check before use.
- No gap was found in the comment-body validation itself (`@NotBlank` + `@Valid` is correctly wired per repo convention).

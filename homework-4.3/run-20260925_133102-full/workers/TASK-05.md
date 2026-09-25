# Worker Report: TASK-05

## Task
SCOPE: src/main/java/io/spring/api/CommentsApi.java
QUESTION: Is the new-comment request DTO validated for required/blank content, and are the article slug and comment id path variables checked before use, per CLAUDE.md's Input Validation section?

## Findings

**New-comment DTO body validation — present and correct.**
- `NewCommentParam` (CommentsApi.java:96-102) has a single field, `body`, annotated `@NotBlank(message = "can't be empty")` (CommentsApi.java:100-101).
- `createComment` (CommentsApi.java:40-51) binds it via `@Valid @RequestBody NewCommentParam newCommentParam` (CommentsApi.java:44), so Spring's Bean Validation will reject blank/missing `body` before the handler body runs. This matches CLAUDE.md's Input Validation guidance ("Validate format and required fields via Bean Validation (`@NotBlank`, `@Email`, etc.)").

**Article slug path variable — checked before use, consistently.**
- `createComment`: `slug` (CommentsApi.java:42) is looked up via `articleRepository.findBySlug(slug).orElseThrow(ResourceNotFoundException::new)` (CommentsApi.java:45-46) before any use.
- `getComments`: same pattern at CommentsApi.java:56-57.
- `deleteComment`: same pattern at CommentsApi.java:72-73.
- In all three handlers, an unknown/malformed slug results in `ResourceNotFoundException` rather than a null-pointer or unchecked lookup — this is existence validation, not format/bounds validation, but it satisfies "checked before use."
- Note: `slug` itself carries no `@Size`/format constraint (it's a raw `@PathVariable String`), so pathologically long or malformed slug values are only implicitly bounded by the `findBySlug` query (parameterized via MyBatis `#{}` binding elsewhere in the mapper, not shown in this file) rather than rejected up front. This is a minor gap relative to CLAUDE.md's "add `@Size` bounds on any new user-supplied string field" guidance, though slug is arguably a URL segment rather than a body field the guidance was aimed at.

**Comment id path variable — checked before use only in `deleteComment`, and the check is on existence, not format.**
- `deleteComment` (CommentsApi.java:67-85): `commentId` (CommentsApi.java:70) is passed to `commentRepository.findById(article.getId(), commentId)` (CommentsApi.java:75), and the `Optional` result is handled via `.map(...).orElseThrow(ResourceNotFoundException::new)` (CommentsApi.java:76-84) — so a non-existent or malformed id yields a 404 rather than being used unchecked.
- There is no explicit format/type validation on `commentId` (e.g., no check that it's numeric/UUID-shaped before hitting the repository) — it relies entirely on the repository lookup returning empty for anything invalid. Given MyBatis parameter binding is used (per CLAUDE.md's Database Access rules) this is not an injection risk, but it does mean malformed ids are indistinguishable from missing ids in the response (both become generic 404s), which is arguably fine from an information-disclosure standpoint (CLAUDE.md: "keep messages returned to clients generic where the specific detail would help an attacker").

## Recommendations

No functional/security gap found in the core requirement: the comment body is validated via `@NotBlank`, and both `slug` and `id` path variables are checked (via repository lookup + `orElseThrow`) before being used in any of the three handlers.

Optional hardening (not a hard CLAUDE.md violation, since `@Size` guidance is framed around "user-supplied string field[s]" typically meaning body fields):
- Consider adding a reasonable `@Size` or pattern constraint on the `slug`/`id` `@PathVariable`s (e.g., via `@Pattern` or a max length) if these values are ever logged or used in downstream systems, to reject obviously-malformed input before it reaches the repository layer, rather than relying solely on "not found" semantics.

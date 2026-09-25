# Worker Report: TASK-04

## Task
SCOPE: src/main/java/io/spring/api/ArticleApi.java
QUESTION: Is the single-article update endpoint's request DTO validated with `@Valid`, and are its path variables checked before use, per CLAUDE.md's Input Validation section?

## Findings

**1. Request DTO is NOT validated with `@Valid`.**
- `ArticleApi.java:44-47` — the `updateArticle` handler signature is:
  ```java
  public ResponseEntity<?> updateArticle(
      @PathVariable("slug") String slug,
      @AuthenticationPrincipal User user,
      @RequestBody UpdateArticleParam updateArticleParam) {
  ```
  The `updateArticleParam` parameter is annotated only with `@RequestBody` — there is no `@Valid` annotation, so Bean Validation is never triggered for this endpoint (contrast with `UsersApi`'s login/registration DTOs, which are the documented convention of pairing `@Valid @RequestBody` per CLAUDE.md's Conventions section).
- Consistent with this, `UpdateArticleParam.java:12-17` (`title`, `body`, `description` fields) declares **no** Bean Validation annotations at all (no `@NotBlank`, no `@Size`), so even if `@Valid` were added today it would have nothing to enforce.
- This directly contradicts CLAUDE.md's Security Requirements → Input Validation: "Validate format and required fields via Bean Validation (`@NotBlank`, `@Email`, etc.); add `@Size` bounds on any new user-supplied string field" and "Treat ALL external input as untrusted... Reject invalid input; do not attempt to sanitize and continue." Currently a caller can PUT an article update with `title`, `body`, and `description` all empty strings (the class's own defaults) or arbitrarily long strings, and it will be accepted and persisted with no rejection.

**2. Path variable `slug` is not format/length checked, but it is existence-checked before use.**
- `ArticleApi.java:45` — `@PathVariable("slug") String slug` has no Bean Validation constraints and no manual format check (e.g. no regex/length bound on the slug string before it's used in a lookup).
- However, it is not used blindly: `ArticleApi.java:48-49` immediately does `articleRepository.findBySlug(slug)`, and `ArticleApi.java:61` throws `ResourceNotFoundException` via `.orElseThrow(...)` if no article matches — so an invalid/non-existent slug is safely rejected via a 404 rather than causing an error further downstream. The slug is passed to MyBatis via parameter binding (repository/mapper layer, out of scope here), so there is no injection risk from the lack of a format check — the gap is narrower than for the request body: it's an omission of defense-in-depth (no `@Size`/pattern bound on the path variable), not a missing existence/authorization check. Authorization is separately enforced at `ArticleApi.java:52-54` via `AuthorizationService.canWriteArticle`.

## Recommendations

- Add `@Valid` to the `updateArticleParam` parameter in `ArticleApi.java:47` (i.e. `@Valid @RequestBody UpdateArticleParam updateArticleParam`).
- Add Bean Validation annotations to `UpdateArticleParam.java` fields — at minimum `@Size` bounds on `title`, `body`, and `description` per CLAUDE.md's requirement ("add `@Size` bounds on any new user-supplied string field"); consider `@NotBlank` if empty updates should be rejected outright (note: current defaults are empty strings, so behavior/semantics of "no change requested" vs. "invalid input" should be clarified with the team before adding `@NotBlank`, since it may be intentional that omitted fields stay empty/unchanged).
- Optionally add a `@Size`/`@Pattern` constraint on the `slug` `@PathVariable` for defense-in-depth, though this is lower priority since the missing-article case is already safely handled via `ResourceNotFoundException`.

# Worker Report: TASK-04

## Task
SCOPE: src/main/java/io/spring/api/ArticleApi.java
QUESTION: Is the single-article update endpoint's request DTO validated with @Valid, and are its path variables checked before use, per CLAUDE.md's Input Validation section?

## Findings

**1. `@Valid` is missing on the update endpoint's request body.**
`ArticleApi.java:43-47`:
```java
@PutMapping
public ResponseEntity<?> updateArticle(
    @PathVariable("slug") String slug,
    @AuthenticationPrincipal User user,
    @RequestBody UpdateArticleParam updateArticleParam) {
```
The `@RequestBody UpdateArticleParam` parameter is annotated with `@RequestBody` only — there is no `@Valid` (or `@Validated`) annotation, so Bean Validation is never triggered for this endpoint, regardless of what constraints exist on the DTO.

**2. The DTO itself carries no Bean Validation constraints at all.**
`src/main/java/io/spring/application/article/UpdateArticleParam.java:12-17`:
```java
public class UpdateArticleParam {
  private String title = "";
  private String body = "";
  private String description = "";
}
```
None of `title`, `body`, or `description` have `@NotBlank`, `@Size`, or any other `javax.validation` annotation. This directly contradicts CLAUDE.md's Input Validation requirement: "Validate format and required fields via Bean Validation (`@NotBlank`, `@Email`, etc.); add `@Size` bounds on any new user-supplied string field." Even if `@Valid` were added to the controller, it would currently have no constraints to enforce — empty strings, arbitrarily long strings, or unbounded payloads for `title`/`body`/`description` would all pass through unchecked into `articleCommandService.updateArticle(...)`.

This is also inconsistent with the rest of the codebase's convention — e.g. `CreateArticleParam`/registration DTOs elsewhere in the app do use `@NotBlank`/`@Size` per the documented "Request DTOs" convention in CLAUDE.md.

**3. The `slug` path variable is not format-validated, but its existence is checked before use.**
`ArticleApi.java:45, 48-49`:
```java
@PathVariable("slug") String slug,
...
return articleRepository
    .findBySlug(slug)
    .map(article -> { ... })
    .orElseThrow(ResourceNotFoundException::new);
```
`slug` is passed straight to `articleRepository.findBySlug(slug)` with no format/length validation (no `@Pattern`, `@Size`, etc.), but the code does check that a matching article exists before any write occurs — `Optional.map(...).orElseThrow(ResourceNotFoundException::new)` means an unrecognized/malformed slug results in a 404 rather than proceeding into `articleCommandService.updateArticle`. Downstream persistence uses MyBatis parameter binding (`#{}`), so this is not a SQL-injection risk per CLAUDE.md's Database Access rule — the gap is purely the absence of upfront format/length validation on the path variable, consistent with how other simple path-variable actions in this codebase (e.g. `ProfileApi.follow`/`unfollow`) are written without such checks. This is a much smaller gap than findings 1–2, since an invalid slug is safely rejected via the 404 path rather than causing unsafe behavior.

## Recommendations

1. Add `@Valid` to the `updateArticleParam` parameter in `ArticleApi.updateArticle` (`ArticleApi.java:47`): `@Valid @RequestBody UpdateArticleParam updateArticleParam`.
2. Add Bean Validation constraints to `UpdateArticleParam` fields, e.g. `@Size(max = <n>)` on `title`, `body`, and `description` (and consider `@NotBlank` if empty updates for these fields are not intended to be allowed — note current defaults are `""`, so semantics need confirming with whoever owns the update-article behavior before adding `@NotBlank`, since partial updates might intentionally allow blank values).
3. Optionally, add a `@Size`/`@Pattern` constraint on the `slug` path variable (would require enabling method-level validation via `@Validated` on the controller class) to reject malformed slugs before hitting the repository — lower priority than #1/#2 since the current behavior already fails safely with a 404.

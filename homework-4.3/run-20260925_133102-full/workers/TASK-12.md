# Worker Report: TASK-12

## Task
SCOPE: `src/main/java/io/spring/application/article/UpdateArticleParam.java`
QUESTION: Does this DTO's Bean Validation annotations enforce the same constraints as `NewArticleParam` where the fields are equivalent, or is any asymmetry between them intentional and documented?

## Findings

**The constraints are not symmetric, and the asymmetry is not documented anywhere near the code that relies on it.**

- `UpdateArticleParam.java:13-16` declares `title`, `body`, `description` as plain `private String` fields defaulted to `""`, with **no Bean Validation annotations at all** (no `@NotBlank`, no `@Size`).
- `NewArticleParam.java:17,21,24` annotates the equivalent fields — `title` (line 17-19, also carrying `@DuplicatedArticleConstraint`), `description` (line 21-22), `body` (line 24-25) — each with `@NotBlank(message = "can't be empty")`.
- Confirming this isn't simply a missed annotation: `ArticleApi.java:47` binds `UpdateArticleParam` via plain `@RequestBody` with **no `@Valid`** on the parameter, whereas Bean Validation on `NewArticleParam` is triggered via `@Valid` in `ArticleCommandService.createArticle(@Valid NewArticleParam ...)` (`ArticleCommandService.java:19`). So even if annotations were added to `UpdateArticleParam`, they would currently go unchecked on the REST path unless `@Valid` were also added.
- The runtime reason blank values must be *allowed* on update is in `Article.java:51-65` (`update(String title, String description, String body)`): each field is only applied `if (!Util.isEmpty(...))`, i.e. a blank/omitted field means "leave this field unchanged" — the endpoint implements partial-update (PATCH-like) semantics despite being exposed as `PUT`. If `@NotBlank` were copied onto `UpdateArticleParam`, clients could no longer submit a title-only or body-only update, breaking this behavior.
- The cross-field duplicate-title check for update (`DuplicatedArticleUpdateValidator.java:23-25`) explicitly special-cases blank titles as valid (`if (Util.isEmpty(title)) return true;`), reinforcing that blank fields are a deliberate, expected input on this DTO — not an oversight.
- However, **nothing in `UpdateArticleParam.java` states this intent**. There is no comment referencing the partial-update behavior in `Article.update`, and CLAUDE.md does not mention this asymmetry either (its "Pitfall" notes cover `<cross-parameter>` propertyPath handling and error-shape inconsistency, not this). Per CLAUDE.md's Security Requirements > Input Validation: "Treat ALL external input as untrusted... Reject invalid input; do not attempt to sanitize and continue" — the current design is a deliberate exception to that default posture for this one DTO, but the exception is undocumented at its source.
- Separately, neither DTO applies `@Size` upper bounds to `title`/`description`/`body`, which CLAUDE.md's Input Validation section calls for ("add `@Size` bounds on any new user-supplied string field"). This gap is symmetric between the two DTOs, so it is not part of the asymmetry being asked about, but it means unbounded strings can currently reach the domain layer via either endpoint.

## Recommendations

1. Add a short comment in `UpdateArticleParam.java` (near the field declarations) explaining that blank/omitted values are intentional and mean "no change to this field," referencing `Article.update` (`core/article/Article.java:51-65`) as the consumer of that contract. This prevents a future contributor from "fixing" the asymmetry by adding `@NotBlank` and silently breaking partial updates.
2. If partial-update semantics are the intended design, consider making that explicit in the type itself (e.g., `Optional<String>`-style fields or a naming/doc convention) rather than relying on `""` vs. non-blank, since the current representation is indistinguishable from "field simply wasn't validated."
3. As a follow-up (not blocking, and not specific to the asymmetry): add `@Size` bounds to `title`, `description`, and `body` on both `NewArticleParam` and `UpdateArticleParam` to close the unbounded-input gap called out in CLAUDE.md's Security Requirements > Input Validation.

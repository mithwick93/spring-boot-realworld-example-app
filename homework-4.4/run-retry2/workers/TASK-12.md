# Worker Report: TASK-12

## Task
SCOPE: src/main/java/io/spring/application/article/UpdateArticleParam.java
QUESTION: Does this DTO's Bean Validation annotations enforce the same constraints as NewArticleParam where the fields are equivalent, or is any asymmetry between them intentional and documented?

## Findings

**The two DTOs are asymmetric, and the asymmetry appears to be an intentional consequence of PUT-as-partial-update semantics — but it is not documented anywhere (no comment in the DTO, no mention in CLAUDE.md).**

- `UpdateArticleParam.java:13-16` declares `title`, `body`, `description` with **no Bean Validation annotations at all**, each defaulted to `""`:
  ```java
  private String title = "";
  private String body = "";
  private String description = "";
  ```
- By contrast, `NewArticleParam.java:17,21,24` puts `@NotBlank(message = "can't be empty")` on the equivalent `title`, `description`, `body` fields, and additionally `@DuplicatedArticleConstraint` on `title` (`NewArticleParam.java:18`).
- The missing `@NotBlank` on `UpdateArticleParam` is consistent with how the value is consumed downstream: `Article.update(String, String, String)` (`core/article/Article.java:51-65`) treats an empty string as "field not supplied" — it only overwrites `title`/`description`/`body` `if (!Util.isEmpty(...))`. So `UpdateArticleParam`'s empty-string default is a deliberate partial-update sentinel, not an oversight of the create-DTO's required-field validation.
- The duplicate-title check is preserved for update, but via a different mechanism than `NewArticleParam`: `ArticleCommandService.java:32` puts `@DuplicatedArticleConstraint(validationAppliesTo = ConstraintTarget.PARAMETERS)` on the `updateArticle` method itself (cross-parameter), delegated to `DuplicatedArticleUpdateValidator.java:19-45`, which explicitly returns `true` (no violation) when `Util.isEmpty(title)` (`DuplicatedArticleUpdateValidator.java:23-25`) — i.e., it only checks for duplicates when a new title is actually being set. This mirrors the "empty means unchanged" convention.
- **However, neither the controller nor the service parameter actually validate `UpdateArticleParam`'s own fields at all**, even if someone later added constraints to it:
  - `ArticleApi.java:44-47` — the `updateArticle` REST handler takes `@RequestBody UpdateArticleParam updateArticleParam` with **no `@Valid`**.
  - `ArticleCommandService.java:32` — `updateArticle(Article article, UpdateArticleParam updateArticleParam)` also has **no `@Valid`** on the `updateArticleParam` parameter; only the method-level cross-parameter `@DuplicatedArticleConstraint` is applied.
  - Compare to the create path, where `ArticleCommandService.java:20` declares `createArticle(@Valid NewArticleParam newArticleParam, User creator)` — `@Valid` there is what makes `NewArticleParam`'s field-level `@NotBlank`/`@DuplicatedArticleConstraint` actually fire (the class carries `@Validated` at `ArticleCommandService.java:12`, but that only enables *method*-level validation; it does not cascade into a nested DTO's own field constraints without `@Valid` on that parameter).
- Per CLAUDE.md's Security Requirements → Input Validation: *"Validate format and required fields via Bean Validation... add `@Size` bounds on any new user-supplied string field."* Neither DTO currently has `@Size` bounds on `title`/`description`/`body` (symmetric gap, not specific to this asymmetry), but `UpdateArticleParam` additionally lacks any length/format bound and lacks the wiring (`@Valid`) that would even let a future bound take effect.

## Recommendations

1. No functional bug today: the observed asymmetry (missing `@NotBlank` on `UpdateArticleParam`) is intentional and correct, given `Article.update()`'s "empty string = leave unchanged" contract. Recommend adding a one-line comment on `UpdateArticleParam` (e.g. above the field block) stating that empty string is a deliberate "no-op" sentinel for partial updates — this is currently only discoverable by cross-referencing `Article.update()` and `DuplicatedArticleUpdateValidator`, and isn't captured in CLAUDE.md's Conventions section.
2. Latent gap worth flagging: because neither `ArticleApi.updateArticle` (`ArticleApi.java:47`) nor `ArticleCommandService.updateArticle` (`ArticleCommandService.java:32`) has `@Valid` on the `UpdateArticleParam` parameter, any future `@Size`/format constraint added directly to `UpdateArticleParam`'s fields would silently never be enforced. If bounding update-field length is ever desired (per CLAUDE.md's `@Size` guidance), add `@Valid` to the parameter at the point the constraint is introduced — otherwise the annotation is a no-op.

# Worker Report: TASK-12

## Task
Scope: `src/main/java/io/spring/application/article/UpdateArticleParam.java`
Question: Does this DTO's Bean Validation annotations enforce the same constraints as `NewArticleParam` where the fields are equivalent, or is any asymmetry between them intentional and documented?

## Findings

**The DTOs are asymmetric, but the asymmetry is intentional and follows an established codebase convention — not an oversight.**

1. `UpdateArticleParam.java:13-16` — `title`, `body`, `description` are plain `String` fields defaulted to `""`, with **zero** Bean Validation annotations (no `@NotBlank`, no `@Size`, nothing).
2. `NewArticleParam.java:17-25` — the equivalent fields (`title`, `description`, `body`) all carry `@NotBlank(message = "can't be empty")`, and `title` additionally carries `@DuplicatedArticleConstraint` (NewArticleParam.java:18).
3. `ArticlesApi.java:34` — the create endpoint binds with `@Valid @RequestBody NewArticleParam`, so the `@NotBlank`/`@DuplicatedArticleConstraint` field annotations are actually triggered.
4. `ArticleApi.java:47` — the update endpoint binds with plain `@RequestBody UpdateArticleParam` (**no `@Valid`**). Since `UpdateArticleParam` has no field-level constraints anyway, this omission currently has no functional effect, but it is a latent trap: if a field-level constraint (e.g. `@Size`) is added to `UpdateArticleParam` later, per CLAUDE.md's Security Requirements → Input Validation guidance ("add `@Size` bounds on any new user-supplied string field"), it will silently never fire without `@Valid` also being added at the call site.
5. `Article.java:51-65` (`Article.update`) treats a blank `title`/`description`/`body` as "leave this field unchanged" (`if (!Util.isEmpty(title)) { ... }` for each field) — i.e. `UpdateArticleParam`'s blank-string defaults are a deliberate partial-update ("PATCH-like") sentinel, not invalid input. This is why `@NotBlank` cannot apply symmetrically to the update DTO — blank is a legitimate value here, whereas on create it is not.
6. `ArticleCommandService.java:31-32` — the title-duplication check that `NewArticleParam` gets via the field-level `@DuplicatedArticleConstraint` is *not* dropped for updates; it is preserved via a method-level cross-parameter constraint: `@DuplicatedArticleConstraint(validationAppliesTo = ConstraintTarget.PARAMETERS)` on `updateArticle(Article, UpdateArticleParam)`, backed by `DuplicatedArticleUpdateValidator.java`. This validator explicitly returns valid (`true`) when `Util.isEmpty(title)` (DuplicatedArticleUpdateValidator.java:23-25), consistent with point 5 — blank title means "don't change the title," so there's nothing to dedupe against.
7. This 3-segment cross-parameter constraint-node construction (`addParameterNode(1).addPropertyNode("title")`) at DuplicatedArticleUpdateValidator.java:41-42 is exactly the pattern CLAUDE.md's Conventions section documents as required to avoid the `<cross-parameter>` 2-segment `propertyPath` pitfall — so this part of the asymmetry (field-level annotation on create vs. method-level cross-parameter validator on update) is explicitly called out and documented in CLAUDE.md.
8. Cross-check against the analogous pattern elsewhere: `UpdateUserParam.java:15-24` (the update-DTO sibling of `UsersApi`'s create param) follows the identical convention — blank-defaulted fields with `@NotBlank` dropped, while a *format* constraint that still makes sense on blank-or-present values (`@Email` at UpdateUserParam.java:18) is retained. `UpdateArticleParam` has no equivalent format-only constraint to retain (title/body/description have no format beyond non-blank), so it ends up with no annotations at all — consistent with, not a deviation from, this codebase-wide partial-update convention.

## Recommendations

No functional gap found: the required-field (`@NotBlank`) asymmetry is intentional partial-update semantics, and the duplicate-title check is preserved via a documented cross-parameter validator mechanism, not dropped.

One low-priority hardening suggestion (not a current bug): add `@Valid` to `ArticleApi.java:47`'s `@RequestBody UpdateArticleParam updateArticleParam` now, even though it's currently a no-op, so that if `@Size` bounds (per CLAUDE.md Security Requirements → Input Validation) are ever added to `UpdateArticleParam`'s fields, they take effect automatically rather than being silently skipped by a maintainer who assumes `@RequestBody` alone is sufficient (as it would be reasonable to assume, since it works that way nowhere else in this codebase — every other validated `@RequestBody` in `UsersApi`/`ArticlesApi` pairs the DTO with `@Valid`).

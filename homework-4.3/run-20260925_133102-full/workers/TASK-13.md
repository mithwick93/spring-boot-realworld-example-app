# Worker Report: TASK-13

## Task
SCOPE: `src/main/java/io/spring/application/article/DuplicatedArticleValidator.java`
QUESTION: Does this validator correctly exclude the article's own current slug when checking for a duplicate title on update, or can a no-op edit be falsely rejected as a duplicate?

## Findings

- `DuplicatedArticleValidator.java:16` — the entire check is:
  `return !articleQueryService.findBySlug(Article.toSlug(value), null).isPresent();`
  It takes only the candidate `title` string (the constraint target type is `ConstraintValidator<DuplicatedArticleConstraint, String>`, line 10). There is no article ID, current slug, or any other contextual parameter available to this validator — it has no mechanism by which it *could* exclude "the article's own current slug," because it never receives the current article at all. The second argument to `findBySlug` is a hardcoded `null` (the `User` used only for building favorite/follow view state in `ArticleQueryService.findBySlug`, unrelated to duplicate exclusion).

- This class is only wired to the **create** path: `NewArticleParam.java:18` applies `@DuplicatedArticleConstraint` directly to the `title` field of `NewArticleParam`, which is exactly the case where "exclude the current article" doesn't apply (there is no current article yet). So in its actual current usage, this file's logic is correct for what it's asked to do.

- The **update** path does not use this class. `ArticleCommandService.java:31` applies the same `@DuplicatedArticleConstraint` annotation but at the method level (`validationAppliesTo = ConstraintTarget.PARAMETERS`), which routes to the sibling cross-parameter validator `DuplicatedArticleUpdateValidator` (`ConstraintValidator<DuplicatedArticleConstraint, Object[]>`). That class correctly excludes the current article: it short-circuits when the new slug equals the existing article's slug (`DuplicatedArticleUpdateValidator.java:27`) and additionally filters out matches on the article's own ID (`DuplicatedArticleUpdateValidator.java:33`). So a no-op title edit is *not* falsely rejected today — but that correctness lives entirely outside the scoped file, in a separate validator class.

- Risk (per CLAUDE.md Security Requirements > Input Validation, "treat all external input as untrusted" / validate at the boundary correctly): the scoped `DuplicatedArticleValidator` is a latent trap for future maintainers. Because `@DuplicatedArticleConstraint` is declared as reusable across both a plain `String` target and an `Object[]` cross-parameter target (see `DuplicatedArticleConstraint.java:14`), it would be easy for a future change to attach this same annotation directly to a `title` field on `UpdateArticleParam` (mirroring the `NewArticleParam` pattern) instead of keeping the method-level cross-parameter form. If that happened, `DuplicatedArticleValidator.isValid` (line 16) would reject *any* update where the title's slug already exists — including a true no-op edit of the article's own unchanged title — because it has no way to compare against the current article's slug/ID. There is nothing in the scoped file itself (no guard, no doc comment) preventing this misuse.

## Recommendations

- No gap in current behavior: as actually wired (create-only, via `NewArticleParam`), `DuplicatedArticleValidator` does not cause false rejections of no-op edits, because updates go through `DuplicatedArticleUpdateValidator` instead.
- Recommend (not required to fix a live bug): add a short comment on `DuplicatedArticleValidator` (or in `DuplicatedArticleConstraint`'s Javadoc) stating explicitly that this `String`-target variant is create-only and must never be applied to an update DTO's title field, since it has no self-exclusion capability — to prevent the latent misuse scenario above from being introduced by a future edit.

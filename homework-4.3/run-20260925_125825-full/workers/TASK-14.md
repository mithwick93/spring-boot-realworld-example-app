# Worker Report: TASK-14

## Task
SCOPE: src/main/java/io/spring/application/article/DuplicatedArticleUpdateValidator.java
QUESTION: Does this cross-parameter validator correctly wire the article-id exclusion into the duplicate-title check on update?

## Findings

- `DuplicatedArticleUpdateValidator.isValid` (DuplicatedArticleUpdateValidator.java:19-45) receives the cross-parameter array `value[0]` = the `Article` being updated and `value[1]` = the `UpdateArticleParam` (lines 20-21).
- Early-out guards are correct and ordered sensibly:
  - Line 23-25: if the new title is blank/null (`Util.isEmpty`), skip the check — no title change, nothing to validate.
  - Line 26-29: if the new slug equals the *current* article's own slug, skip — the article isn't actually renaming into a different slug, so it can't collide with itself.
- The actual exclusion is wired correctly at line 30-34:
  ```java
  boolean duplicated =
      articleRepository
          .findBySlug(newSlug)
          .filter(existing -> !existing.getId().equals(article.getId()))
          .isPresent();
  ```
  This looks up any existing article with the candidate slug, then filters out the case where the match *is* the article currently being updated (by `getId()` equality). Only a match belonging to a **different** article ID counts as a true duplicate. This is the correct exclusion pattern for an update-path uniqueness check (as opposed to the create-path validator, which has no article to exclude).
- The violation is redirected to a 3-segment property path via `addParameterNode(1).addPropertyNode("title")` (lines 38-43), per the convention documented in CLAUDE.md ("Pitfall — cross-parameter Bean Validation constraints must not use the default `<cross-parameter>` node"). This correctly avoids the `<cross-parameter>` propertyPath bug called out for both `CustomizeExceptionHandler.getParam` and `GraphQLCustomizeExceptionHandler.getParam`. `addParameterNode(1)` points at `value[1]` (the `UpdateArticleParam`), matching where `title` actually lives, so the field-name mapping in both exception handlers will resolve to `title` correctly.
- Relative to CLAUDE.md's Security Requirements > Input Validation ("Treat ALL external input as untrusted... Reject invalid input; do not attempt to sanitize and continue"): the validator treats the incoming `title` as untrusted, derives a slug from it via `Article.toSlug(title)`, and rejects (returns `false`) rather than silently coercing/truncating on collision — consistent with that requirement.
- Database access uses `articleRepository.findBySlug(newSlug)` (a `core` repository interface backed by MyBatis parameter binding under the hood, not raw SQL concatenation here), consistent with CLAUDE.md's Database Access requirement to use `#{param}` binding only — this file itself does no SQL, so there's nothing to flag beyond confirming it doesn't bypass the repository abstraction.
- **Known caveat already documented in CLAUDE.md, not a defect in this file**: CLAUDE.md's Test Plan section flags `Duplicated*Validator` classes as having "a documented history of TOCTOU races." This check-then-update flow (`findBySlug` here, followed by the actual persistence update elsewhere in the update command handler) has an inherent check-then-act race window between this validation call and the actual write — two concurrent updates could both pass this check before either commits. This is a structural characteristic of the validate-then-persist pattern, not something wired incorrectly in this specific file, but worth flagging given the repo's documented history here.

## Recommendations

No gap found in the article-id exclusion wiring itself — `value[0]`/`value[1]` indexing, the slug-equality short-circuit, the `getId()` exclusion filter, and the 3-segment property-path redirection are all correctly implemented and consistent with the repo's documented conventions.

The one actionable item is not a bug in this file but a reminder tied to the repo's documented TOCTOU history: if a stronger uniqueness guarantee is needed, consider enforcing it with a unique constraint at the database/migration level (`db/migration/V1__create_tables.sql` or a follow-up migration) as a backstop, since this validator alone cannot close the check-then-act race window under concurrent updates to the same target slug.

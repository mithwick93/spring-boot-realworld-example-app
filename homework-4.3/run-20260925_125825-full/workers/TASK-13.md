# Worker Report: TASK-13

## Task
SCOPE: `src/main/java/io/spring/application/article/DuplicatedArticleValidator.java`
QUESTION: Does this validator correctly exclude the article's own current slug when checking for a duplicate title on update, or can a no-op edit be falsely rejected as a duplicate?

## Findings

`DuplicatedArticleValidator` (DuplicatedArticleValidator.java:9-18) implements `ConstraintValidator<DuplicatedArticleConstraint, String>` — a single-value (not cross-parameter) validator. Its `isValid` (line 15-17) does an unconditional lookup:

```java
return !articleQueryService.findBySlug(Article.toSlug(value), null).isPresent();
```

There is no parameter, field, or lookup here that excludes "this article's own slug" — the method only ever sees the raw title string, with no article identity to compare against. In isolation, if this validator were invoked on an update where the title is unchanged, it would find the article's own existing slug and reject it as a duplicate — a false-positive/no-op-rejected-as-duplicate bug.

**However, this validator is not actually wired to the update path.** Tracing the annotation usage:
- `DuplicatedArticleConstraint` is applied directly to `NewArticleParam.title` (NewArticleParam.java:18), which is validated via `@Valid` on `ArticleCommandService.createArticle` (ArticleCommandService.java:19). On creation there is no "own" article yet, so unconditionally rejecting any existing slug is the *correct* behavior — `DuplicatedArticleValidator` is scoped to create-only.
- The update path, `ArticleCommandService.updateArticle` (ArticleCommandService.java:31-32), is annotated `@DuplicatedArticleConstraint(validationAppliesTo = ConstraintTarget.PARAMETERS)`, which routes validation to the cross-parameter validator `DuplicatedArticleUpdateValidator` (a separate class, `ConstraintValidator<DuplicatedArticleConstraint, Object[]>`), not to `DuplicatedArticleValidator`. That validator explicitly guards the no-op case at DuplicatedArticleUpdateValidator.java:26-29 (`if (newSlug.equals(article.getSlug())) return true;`) and excludes the article's own id at line 30-34 (`.filter(existing -> !existing.getId().equals(article.getId()))`).

So within the file in scope, no self-exclusion logic exists — but by design it doesn't need any, because it is never invoked on the update flow. The self-exclusion responsibility correctly lives in `DuplicatedArticleUpdateValidator`, not here.

Per CLAUDE.md's Security Requirements > Input Validation ("Validate format and required fields... Reject invalid input; do not attempt to sanitize and continue"), this validator does correctly reject untrusted/duplicate title input on the one path it governs (create); it neither validates nor needs to validate self-exclusion since that is out of scope for creation.

## Recommendations

No gap found in `DuplicatedArticleValidator.java` itself. It is correctly restricted to the create path (`NewArticleParam`/`createArticle`) where no self-slug exclusion is needed, and the update path uses the separate, properly self-excluding `DuplicatedArticleUpdateValidator`.

One maintainability note (not a bug): the shared `@DuplicatedArticleConstraint` annotation resolves to two very differently-behaved validators (`DuplicatedArticleValidator` for `String` targets, `DuplicatedArticleUpdateValidator` for `Object[]`/cross-parameter targets) purely based on which target type is annotated. This is easy to misread in isolation (as this task's phrasing shows) and worth a doc comment on `DuplicatedArticleConstraint` clarifying that the single-value validator is create-only and never exclude-self-aware, so a future engineer doesn't try to reuse `DuplicatedArticleValidator` directly on an update-path field expecting self-exclusion.

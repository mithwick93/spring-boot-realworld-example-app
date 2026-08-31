# Task Checklist — fix DuplicatedArticleValidator self-exclusion bug

Implementing Run 2's plan from `context-curation-2026-08.md` (1.9): a cross-parameter Bean
Validation constraint on `ArticleCommandService.updateArticle`, reusing the existing
`ConstraintViolationException` path on both REST and GraphQL — no new exception-handler code.

## Steps

- [x] Add `DuplicatedArticleUpdateValidator` (`ConstraintValidator<DuplicatedArticleConstraint, Object[]>`,
      `@SupportedValidationTarget(ValidationTarget.PARAMETERS)`): blank title → valid (no-op
      per `Article.update()`'s "blank means unchanged"), new slug equals current slug → valid
      (no-op rename), new slug taken by a *different* article → invalid.
- [x] Update `DuplicatedArticleConstraint`'s `@Constraint(validatedBy = {...})` to list both
      `DuplicatedArticleValidator` (still used by `NewArticleParam.title`) and the new
      cross-parameter validator.
- [x] Remove `@DuplicatedArticleConstraint` from `UpdateArticleParam.title`.
- [x] Annotate `ArticleCommandService.updateArticle` itself with `@DuplicatedArticleConstraint`;
      drop the now-inert `@Valid` on the `updateArticleParam` parameter.
- [x] Drop `@Valid` from `ArticleApi.updateArticle`'s `@RequestBody UpdateArticleParam`
      parameter (and the now-unused `javax.validation.Valid` import).
- [x] Add `ArticleCommandServiceTest` (real `@SpringBootTest` + test DB, not mocked — existing
      `ArticleApiTest` mocks the service and never exercises the real validator): three tests —
      unchanged title succeeds, genuine rename succeeds, collision with a different article's
      title is rejected.
- [x] `./gradlew test` — full suite, no regressions.
- [x] `./gradlew spotlessJavaApply`.
- [x] `/review-changes` on the staged diff — confirm delegation to the `reviewer` subagent.
- [x] Commit. (`954c511`)

## Decisions Made

- **Cross-parameter violation must not be left on the default `<cross-parameter>` node.** Verified
  via bytecode (`NodeImpl.CROSS_PARAMETER_NODE_NAME = "<cross-parameter>"`) that a raw cross-param
  violation produces `propertyPath = "updateArticle.<cross-parameter>"` (2 segments). Both
  `CustomizeExceptionHandler.getParam` (REST) and `GraphQLCustomizeExceptionHandler.getParam`
  (GraphQL) special-case/derive field name differently for 2-segment paths — REST would surface
  the literal field key `"<cross-parameter>"`, GraphQL would surface `""` (empty string,
  colliding with other empty-field errors in `errorsToMap`). Fixed by having
  `DuplicatedArticleUpdateValidator` call `context.disableDefaultConstraintViolation()` +
  `.buildConstraintViolationWithTemplate(...).addParameterNode(1).addPropertyNode("title").addConstraintViolation()`
  on failure, producing a 3-segment path (`updateArticle.<param>.title`) that both handlers'
  existing "else" branch already joins correctly into `"title"` — same convention already used
  for cascaded `@Valid` field violations (e.g. `createArticle.newArticleParam.title`). No
  exception-handler code changed, consistent with the plan's constraint.
- **Test base: full `@SpringBootTest`, not the `@MybatisTest` slice.** Confirmed via
  `mybatis-spring-boot-test-autoconfigure`'s `spring.factories` that `@MybatisTest` (what
  `DbTestBase`/`ArticleQueryServiceTest` use) does NOT import `ValidationAutoConfiguration`, so no
  `MethodValidationPostProcessor` bean exists there and `@Validated`/`@DuplicatedArticleConstraint`
  would silently never fire — a slice-based test would falsely pass regardless of validator
  correctness. `ArticleRepositoryTransactionTest` (full `@SpringBootTest` + `@AutoConfigureTestDatabase(replace = NONE)`,
  no mocks) is the correct pattern to copy instead; matches what the checklist already specified.
- **Wrong package for `SupportedValidationTarget`/`ValidationTarget`.** First `./gradlew test`
  attempt failed at `:compileJava` with "cannot find symbol" for both types. Root cause: they
  live in `javax.validation.constraintvalidation`, not `javax.validation` directly (verified via
  `unzip -l` on both `validation-api-1.1.0.Final.jar` and `jakarta.validation-api-2.0.2.jar` —
  both agree on the package). Fixed the two imports in `DuplicatedArticleUpdateValidator.java`;
  `compileJava`/`compileTestJava` now succeed.
- **`DuplicatedArticleConstraint` needed a `validationAppliesTo()` element.** Second
  `./gradlew test` run (this one compiled) failed 6 tests, 4 of them with
  `ConstraintDefinitionException`: "mixes generic and cross-parameter validation" — per the Bean
  Validation spec, once a constraint has both a generic validator (`DuplicatedArticleValidator`,
  still used by `NewArticleParam.title`) and a cross-parameter validator
  (`DuplicatedArticleUpdateValidator`) registered, any method-level usage is ambiguous
  (PARAMETERS vs RETURN_VALUE) and the annotation must declare
  `ConstraintTarget validationAppliesTo() default ConstraintTarget.IMPLICIT;`. Fixed by adding
  that element to `DuplicatedArticleConstraint.java` and setting
  `@DuplicatedArticleConstraint(validationAppliesTo = ConstraintTarget.PARAMETERS)` on
  `ArticleCommandService.updateArticle`. Field-level usage on `NewArticleParam.title` is
  unaffected (generic-only there, no ambiguity). **Not yet re-verified with a full test run —
  do that first, before touching anything else.**

## Handoff — read this before continuing

**Where things stand:** the core fix (steps 1–6) is implemented and compiles clean
(`./gradlew compileJava compileTestJava` passes). Two full `./gradlew test` runs have been done;
both found real bugs, both bugs have candidate fixes applied but **neither fix has been
re-verified by a test run yet**. Do not assume green — run `./gradlew test` again next.

**What's fixed and needs re-verification:**
1. Import path bug (`javax.validation.constraintvalidation.*`) — see Decisions Made above. Confirmed via `compileJava`.
2. `validationAppliesTo()` missing on `DuplicatedArticleConstraint` — see Decisions Made above. NOT yet run through tests.

**What's diagnosed but only half-fixed — finish this next:**
`ArticleCommandServiceTest.java` (`src/test/java/io/spring/application/article/`) hardcodes a
single user email (`article-command-service@test.com`) and article titles (`"article one"`,
`"article two"`) in `@BeforeEach setUp()`. The repo's shared in-memory SQLite DB has **no
per-test cleanup/rollback** (confirmed — no `@Sql`, no `@Transactional` rollback in this repo's
test setup), and every test method re-runs `setUp()`, so the 2nd/3rd test in the class fail with
`UNIQUE constraint failed: users.email`. I added `import java.util.UUID;` to the test file but
**have not yet actually changed the email/title values** — that's the next concrete edit. Also
worth checking: `articles.slug` has a `UNIQUE` constraint too (confirmed via
`V1__create_tables.sql`), so hardcoded titles like `"article one"` could collide with articles
created by *other* test classes in the same JVM run, not just within this class — use random
suffixes (e.g. `UUID.randomUUID()`) for email, username, and article titles, not just email.

**Files touched so far (all edits already applied, not yet re-tested):**
- `src/main/java/io/spring/application/article/DuplicatedArticleUpdateValidator.java` — new file, cross-parameter validator (fixed import path bug).
- `src/main/java/io/spring/application/article/DuplicatedArticleConstraint.java` — added `validationAppliesTo()` + `ConstraintTarget` import.
- `src/main/java/io/spring/application/article/UpdateArticleParam.java` — removed field-level `@DuplicatedArticleConstraint` from `title`.
- `src/main/java/io/spring/application/article/ArticleCommandService.java` — `updateArticle` now `@DuplicatedArticleConstraint(validationAppliesTo = ConstraintTarget.PARAMETERS)`, dropped inert `@Valid`.
- `src/main/java/io/spring/api/ArticleApi.java` — dropped `@Valid` on `updateArticle`'s `@RequestBody` param + unused import.
- `src/test/java/io/spring/application/article/ArticleCommandServiceTest.java` — new test, **still needs the UUID fix described above**.

**Next steps in order:**
1. Finish the `ArticleCommandServiceTest.java` fix — unique email/username/titles per test (UUID-based), matching this repo's existing convention.
2. Re-run `./gradlew test` (delegate to a subagent per user's standing instruction: report back ONLY pass/fail + failing test names/reasons, no raw output, under 200 words). `export JAVA_HOME=/Users/mitwic/Library/Java/JavaVirtualMachines/temurin-11.0.27/Contents/Home` is required (system default is Java 21).
3. If green: check off the `./gradlew test` step below, then run `./gradlew spotlessJavaApply` (same JAVA_HOME requirement) and check off that step.
4. Stage the diff and run `/review-changes` (delegates to the read-only `reviewer` subagent — do not self-review).
5. Do NOT commit until the user explicitly asks. When they do: no `Co-Authored-By: Claude` trailer (global user rule).
6. Fill in "Where each fact belongs" below before wrapping up.

## Current Status

Done. Test-isolation fix applied (UUID-suffixed email/username/titles), full suite green
twice (71/71, including a final post-commit re-verification), `/review-changes` approved
with no violations, committed as `954c511`. Cross-parameter validation pitfall documented in
`CLAUDE.md` for the team. Nothing left on the Steps list.

## Where each fact belongs

(after the handoff/decisions step — pick 3 real facts from this session, one line of
reasoning each, and say which of task-checklist.md / auto memory / CLAUDE.md each belongs in)

1. **Cross-parameter Bean Validation constraints must not use the default `<cross-parameter>`
   node, and mixing a generic + cross-parameter validator on one `@Constraint` requires
   `validationAppliesTo()`.** → **CLAUDE.md.** This is a reusable, team-wide pitfall tied to this
   repo's exception-handling code (`CustomizeExceptionHandler`/`GraphQLCustomizeExceptionHandler`),
   not specific to my machine or this session — any future contributor adding a cross-parameter
   validator will hit it. Added as a new bullet under Conventions. Done.
2. **`./gradlew` needs `JAVA_HOME` pointed at a Java 11 temurin install; system default is
   Java 21 and breaks the build (`Unsupported class file major version 65`).** → **auto memory
   (project).** This is specific to my local machine's Java installs, not a codebase fact another
   contributor's CLAUDE.md read would need — so memory, not CLAUDE.md. Saved as
   `project_java_version`.
3. **User wants every `./gradlew test` run delegated to a subagent that reports back only
   pass/fail + failing test names, never raw output.** → **auto memory (feedback).** This is a
   personal workflow preference of the user's (confirmed twice: once in this checklist's prior
   handoff notes, once restated explicitly mid-session), not a codebase convention — feedback
   memory, not CLAUDE.md. Saved as `feedback_delegate_gradle_test`.

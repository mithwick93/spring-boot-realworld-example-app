# context-curation-2026-08

## The task

`DuplicatedArticleValidator` (`src/main/java/io/spring/application/article/DuplicatedArticleValidator.java:16`)
rejects `PUT /articles/{slug}` requests that resubmit an article's own current title, because
it's a field-level Bean Validation constraint that only ever sees the raw title string — it
has no way to know which article is currently being edited. I flagged this in
`extension-stack-2026-08.md` (1.6) and left it deliberately unfixed as review material. It's
a good fit for this exercise because the correct fix depends on a handful of specific facts
(the validator's exact code, this repo's dual REST/GraphQL exception-handler convention, how
`Article.update()` treats blank fields) rather than the whole repo — which is exactly the
"does full context add anything beyond cost" question this homework is testing.

Ran the same ask in two fresh sessions, both stopped at plan stage (rejected the plan rather
than implementing) so the comparison is plan-vs-plan, not muddied by two separate
implementations.

## Run 1 — full context

Prompt:

> Fix the `DuplicatedArticleValidator` bug I flagged in `extension-stack-2026-08.md`:
> updating an article while keeping its own current title gets falsely rejected as a
> duplicate. Investigate whatever files you need in this repo, then propose and implement a
> fix.

What happened: two backgrounded Explore agents — one to find the validator, the bug note,
call sites, and tests (2m 9s), a second specifically to "investigate how field-level errors
are normally constructed outside of bean validation, so the fix can produce a consistent
`{"errors": {...}}` response for both REST and GraphQL" (1m 42s, searched 3 patterns, read 7
files, ran 1 shell command). Total 7m 29s before the plan was ready.

Plan (verbatim):

- Root cause: correctly identified — `ConstraintValidator<DuplicatedArticleConstraint, String>`
  only ever receives the raw title, so no fix is possible inside the validator; the check
  needs the article's identity, which is only available in `ArticleApi.updateArticle` /
  `ArticleCommandService.updateArticle`.
- Fix: remove `@DuplicatedArticleConstraint` from `UpdateArticleParam.title`, move the
  duplicate-slug check into `ArticleCommandService.updateArticle` as manual logic against
  `articleRepository.findBySlug`, and on collision throw a **new** `InvalidRequestException`
  (`BeanPropertyBindingResult` + `errors.rejectValue(...)`) to preserve the REST
  `{"errors": {"title": [...]}}` shape via the existing `CustomizeExceptionHandler.handleInvalidRequest`.
- Because the check moves out of Bean Validation, `GraphQLCustomizeExceptionHandler` loses its
  existing `ConstraintViolationException` coverage for this case — so the plan adds a **new**
  branch to `GraphQLCustomizeExceptionHandler.onException` for `InvalidRequestException`,
  mirroring the existing branch, specifically to avoid degrading to a generic unhandled-
  exception error on the GraphQL side.
- New unit test file, three tests, plus a note to re-verify two existing tests still pass
  after removing the field constraint.

## Run 2 — curated context

Prompt:

> Here's everything you need to fix a validator bug — no need to explore the repo further.
>
> The bug (from `extension-stack-2026-08.md`): `DuplicatedArticleValidator.isValid` is
> `return !articleQueryService.findBySlug(Article.toSlug(value), null).isPresent();` — it
> rejects if ANY article already has that slug. On an update, re-submitting the article's own
> unchanged title gets falsely flagged as a duplicate, because the validator has no way to
> know which article is currently being updated.
>
> `Article.update()` semantics: all three `UpdateArticleParam` fields use "blank means leave
> unchanged" — `Util.isEmpty` guards, per `Article.java`.
>
> Known convention (`CLAUDE.md`/`reviewer.md`): `javax.validation` annotations go directly on
> DTO fields via `@Valid @RequestBody`; any uniqueness check is expected to guard against the
> TOCTOU pattern (read-check + non-atomic write, no DB unique constraint) — treat this as a
> validation-context problem, not a symptom patch.
>
> Fix `DuplicatedArticleValidator` (or the constraint's wiring) so updating an article to its
> own current title isn't flagged as a duplicate, while still catching a genuine collision
> with a different article's slug.
>
> Reminder: the core problem is exactly point 1 — the validator has no access to which
> article's own slug should be excluded. Solve that, don't just special-case the no-op title.

What happened: one backgrounded Explore agent ("explore article validator usage and update
flow," 1m 38s, searched 5 patterns, read 10 files, ran 1 shell command) — no second agent went
looking for exception-plumbing alternatives, because the prompt's framing ("validation-context
problem, not a symptom patch") kept the question narrow: *where can this validator see the
article's identity*, not *how do I build an error response from scratch*.

Plan (verbatim):

- Root cause: same diagnosis as Run 1, stated more precisely — both `ArticleApi.updateArticle`
  (REST) and `ArticleMutation.updateArticle` (GraphQL) already resolve the real `Article`
  before calling `ArticleCommandService.updateArticle`, so that's where identity is available.
- Fix: a **cross-parameter** Bean Validation constraint — annotate
  `ArticleCommandService.updateArticle` itself with `@DuplicatedArticleConstraint`, backed by a
  second validator (`DuplicatedArticleUpdateValidator`) implementing
  `ConstraintValidator<DuplicatedArticleConstraint, Object[]>` with
  `@SupportedValidationTarget(ValidationTarget.PARAMETERS)`, seeing both `article` and
  `updateArticleParam` together. Blank title → no-op (valid). New slug equals the article's
  own current slug → no-op rename (valid). New slug already taken by a *different* article →
  reject.
- The violation is a plain `ConstraintViolationException` — the **same** exception both
  `CustomizeExceptionHandler.handleConstraintViolation` and
  `GraphQLCustomizeExceptionHandler.onException` already handle today. No new exception type,
  no new handler branch on either adapter. The plan says this explicitly: "avoids the 'fixed
  one adapter, not the other' pitfall called out in `.claude/agents/reviewer.md`."
- Flags a real test gap Run 1 didn't mention: `ArticleApiTest` mocks `ArticleCommandService`
  directly, so no existing test ever actually runs the validator. New test file uses a real
  `@SpringBootTest` + test DB (matching `ArticleRepositoryTransactionTest`'s pattern) so the
  three new tests exercise the real validator, not a mock.

## The check

Does the plan (a) correctly identify that a field-level Bean Validation constraint can't see
which article is being updated, (b) fix it **without** adding new or duplicated
exception-handling wiring on either the REST or GraphQL adapter — this codebase has hit the
"fixed one adapter, not the other" bug before (1.4, 1.7) — and (c) still reject a genuine
collision with a different article's slug?

## Comparison against the check

| | (a) root cause | (b) no new exception wiring | (c) genuine collision still caught |
|---|---|---|---|
| Run 1 (full) | yes | **no** — adds a new `InvalidRequestException` branch to `GraphQLCustomizeExceptionHandler` | yes |
| Run 2 (curated) | yes, more precisely | **yes** — reuses the existing `ConstraintViolationException` path on both adapters, zero new handler code | yes, plus catches slug-equivalent titles (not just literal string equality) |

Run 2 wins on the check. Run 1's plan is correct but architecturally heavier — new exception
type, new handler branch, more surface area for exactly the two-adapter-parity bug this repo
has already shipped twice. Run 2 also caught a real test gap (`ArticleApiTest` never exercises
the actual validator) that Run 1's plan didn't surface.

## Evaluate

- **Which run answered better, and why:** Run 2, on the stated check — fewer new moving parts,
  explicitly reasons about adapter parity, and it's the same conclusion whether or not that
  parity fact was spelled out in the prompt (it wasn't, this time — I revised the curated
  prompt after Run 1 to spell it out, but the version actually run was the *original*, less
  specific one, and it still landed there via its own file reads).
- **What did the full history actually add, beyond cost and latency?** In this case, a worse
  instinct. The second Explore agent in Run 1 was tasked with "how are field-level errors
  normally constructed outside of bean validation" — a reasonable question given no framing
  pointed away from it — and that question's answer (there's a `InvalidRequestException`
  mechanism) became the seed of a heavier, less parity-safe fix. Full context here didn't fail
  to find the right files; it found an equally real but worse path and committed to it because
  nothing in the prompt ruled it out.
- **Did curation actually reduce total context used?** Only partly. The curated prompt didn't
  stop the agent from exploring — it still read 10 files via its own Explore agent. What it
  changed was the *direction* of that exploration: one focused agent instead of two, and no
  detour into exception-plumbing alternatives, because "treat this as a validation-context
  problem, not a symptom patch" foreclosed that branch before the agent went looking.
- **Failure mode / technique:** this task fit comfortably in one window either way, so none of
  the four techniques were forced by size — but the technique that's already implicitly at
  work here is sub-agent isolation: both runs used a backgrounded Explore agent that read many
  files and reported back a summary, not raw file contents, keeping the main thread's context
  small regardless of how much the agent read. See the sketch below for scaling that up
  deliberately rather than incidentally.

## Extend — context budget sketch

Failure mode this doesn't yet handle: the same fix pattern applied across every uniqueness
validator in a much larger codebase — too many validators, conventions, and call sites for one
window, and the two runs above show that under-framed exploration drifts toward heavier fixes.

- **Technique:** sub-agent isolation, one Explore/fix-planning agent per validator.
- **What it holds in the main thread:** a running table of validator name, file:line, and a
  one-line compliance verdict against the shared invariant below — not the file contents.
- **What it drops:** the raw source of every validator once its sub-agent reports back; the
  main thread never accumulates file text across validators.
- **The shared invariant every sub-agent must carry, not just discover fresh:** "reuse an
  existing exception path already handled on both REST and GraphQL adapters; don't invent a
  new one." This has to travel *with* each sub-agent's task, not be left for it to rediscover —
  Run 1's Explore agent had no such constraint and drifted toward inventing one.
- **The one thing that breaks if it drops the wrong item:** if a sub-agent's summary reports
  "fixed: yes/no" without also reporting adapter-parity compliance, the main thread can't tell
  a Run-2-quality fix from a Run-1-quality one across 20 validators — it would approve fixes
  that individually work but collectively reintroduce the parity bug at scale.

## Verify

Run 2 (curated) is at least as good as Run 1 (full) on the stated check — it's better on two
of three criteria and equal on the third. The technique choice for the sketch (sub-agent
isolation, carrying the shared invariant explicitly) is tied to what actually went wrong in
Run 1 — an under-framed exploration finding a real-but-worse path — not picked for novelty.

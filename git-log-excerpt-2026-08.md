# Git Log Excerpt: POST /articles/{slug}/report (Homework 2.5)

Real `git log` output covering the four-phase workflow's docs commit through both
implementation tasks, small-change/verify/commit discipline, one task per commit.

```
commit d772787fe20e03518e402af76b03e7af04a648de
Author: Mithila Wickramarathne <mithila.wickramarathne@etraveligroup.com>
Date:   Wed Sep 2 15:07:17 2026 +0200

    feat: article report REST controller and error wiring (2.3 task 2 / 2.5)

commit e46dd2f6435e81786fc99a0fffbf964e138a36d5
Author: Mithila Wickramarathne <mithila.wickramarathne@etraveligroup.com>
Date:   Wed Sep 2 14:03:33 2026 +0200

    feat: article report persistence layer (2.3 task 1)

    Adds the ability to save a report and reject a duplicate at the DB level
    (composite PK on article_id, user_id), no HTTP endpoint yet. save() doesn't
    check-then-insert (unlike MyBatisArticleFavoriteRepository) since a duplicate
    must be rejected, not silently no-op'd.

    Verify step caught a real gap the plan flagged as a risk but hadn't confirmed:
    this project's SQLite datasource doesn't get Spring's DataIntegrityViolationException
    translation for constraint violations -- it surfaces as UncategorizedSQLException
    wrapping org.sqlite.SQLiteException. Fixed by also catching that case and checking
    the SQLite result code. 75/75 tests passing.

commit 6b640c34f1bf99ae4896e5ef8678d59bddac5421
Author: Mithila Wickramarathne <mithila.wickramarathne@etraveligroup.com>
Date:   Wed Sep 2 14:00:15 2026 +0200

    docs: four-phase workflow plan and tasks for article report feature (2.3)
```

**Not part of this feature's chain, but landed in between the two task commits above:**
`75383b5` ("fix: handle DB-level UNIQUE constraint violation on signup race (2.2)") is
homework 2.2's task 1, a different feature (the signup-race fix) committed the same session
— noted here only so the log excerpt above isn't misread as this feature's own history.

## Task-by-task discipline

- **Task 1** (`e46dd2f`): one small, verifiable change — persistence layer only, no HTTP
  surface. Verified with `./gradlew test --tests "*MyBatisArticleReportRepositoryTest*"`
  before the full suite, caught the SQLite exception-translation gap during Verify (not
  assumed at Plan time), fixed, re-verified (75/75), then committed. Stopped there, per the
  homework's own instruction to implement task 1 only in that pass.
- **Task 2** (`d772787`, this homework): same discipline one task later — REST controller +
  error wiring only, no persistence-layer changes. Verified with the new
  `ArticleReportApiTest` (7/7) before the full suite (82/82), caught a real AC6
  validation-message mismatch between the plan's DTO sketch and the spec's exact wire
  contract during implementation (not from precedent — see `tasks-2026-08.md`'s Task 2
  status note and `spec-2026-08.md`'s Implementation Status section for the full finding),
  fixed, re-verified, then committed.

## Spec status

`spec-2026-08.md`'s Implementation Status section (added this pass) confirms all seven
acceptance criteria are implemented and passing against the real controller and repository,
not mocked or assumed — see that file for the exact commit references and the one real
finding from this task.

## Evaluate (added 2026-09-03, checked against the current homework text)

**How often did verification fail on the first try?** Zero times for this homework's own task
(task 2, `d772787`) — `ArticleReportApiTest` passed 7/7 and the full suite 82/82 on the first
real run. That's not because nothing needed catching: the AC6 validation-message mismatch
(`plan-2026-08.md`'s DTO sketch used "can't be empty," but `spec-2026-08.md`'s AC6 requires
"can't be blank") was caught by comparing the DTO against the spec *before* running the tests,
not by watching a test fail. Contrast with task 1 (`e46dd2f`, the prior task in this same
chain, done in 2.3): that one genuinely failed on the first run — the repository test hit
`UncategorizedSQLException` instead of the assumed `DataIntegrityViolationException` — a real
example of verification catching something review alone hadn't.

**Did any task turn out larger than the plan suggested?** No, for task 2 specifically —
`tasks-2026-08.md` scoped it to exactly three files (`ArticleReportApi.java`,
`CustomizeExceptionHandler.java`, `ArticleReportApiTest.java`), and `d772787`'s diff is exactly
those three files, no more, no less. Accurately scoped, not a case that needed splitting.

**Smallest and largest commit in this feature's history — could either have been split
differently?** By files touched, `e46dd2f` (task 1) is the largest at 8 files / 200
insertions, and `d772787` (task 2) is the smallest by file count at 3 files — but `d772787`
is actually the larger commit *by insertions* (260, since `ArticleReportApiTest.java` alone
is 171 lines). Neither needed splitting further: `e46dd2f`'s 8 files are all one cohesive
unit (migration + entity + repository + mapper + exception + test, none independently
useful without the others), and `d772787`'s 3 files are the minimum needed to wire one
endpoint end-to-end. The split that mattered was the one already made — task 1 vs. task 2 as
separate commits, not any further subdivision within either.

**Risk-profile difference vs. "implement the whole feature, then commit":** if task 1
(persistence) and task 2 (REST controller) had been built and committed together, the SQLite
exception-translation gap found during task 1's Verify step would have surfaced only after the
REST layer already existed on top of it — making it genuinely harder to tell whether the bug
was in the persistence layer or the controller layer, since both would be new and unverified
at the same time. Because task 1 was verified and committed alone first, that gap was cleanly
isolated to `MyBatisArticleReportRepository` before `ArticleReportApi` was even written,
with nothing else to confuse the diagnosis.

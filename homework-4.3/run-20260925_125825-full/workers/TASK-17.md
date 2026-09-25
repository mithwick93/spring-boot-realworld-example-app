# Worker Report: TASK-17

## Task
SCOPE: `src/main/java/io/spring/application/user/DuplicatedEmailValidator.java`
QUESTION: Does this validator have the same TOCTOU exposure (check-then-insert, no DB unique constraint backing it) as the article-title duplicate check, and is that a known, accepted risk per CLAUDE.md?

## Findings

- `DuplicatedEmailValidator.isValid` (`DuplicatedEmailValidator.java:14-16`) does a plain check-then-act: it calls `userRepository.findByEmail(value).isPresent()` and returns `true`/`false` with no locking or transactional guarantee. Two concurrent registrations with the same email can both pass this check before either insert commits — so the *pattern* is the same TOCTOU shape as the article-title duplicate validator.
- **Key difference from the article-title case: this one *is* backed by a DB unique constraint.** `src/main/resources/db/migration/V1__create_tables.sql:5` defines `email varchar(255) UNIQUE` on the `users` table. The article `title` column (`V1__create_tables.sql:14`, `title varchar(255)`) has no such constraint. So `DuplicatedEmailValidator` cannot let a genuine duplicate email land in the database even if the race is lost — the second concurrent insert fails at the DB layer instead of silently succeeding.
- That DB-level failure is explicitly caught and turned into a clean client response: `CustomizeExceptionHandler.handleDataIntegrityViolation` (`src/main/java/io/spring/api/exception/CustomizeExceptionHandler.java:83-92`) handles `DataIntegrityViolationException` and returns `422 Unprocessable Entity` instead of leaking a raw DB error. Its inline comment (lines 86-91) explicitly names this as "a safety net for the race window between the application-layer duplicate-username/email check ... and the actual insert" and confirms the DB `UNIQUE` constraint is "the only thing that catches the second one" — i.e. the maintainers already know about and have mitigated this exact race for username/email.
- CLAUDE.md's Security Requirements > Input Validation section requires rejecting invalid input outright, and the Test Plan section calls out "the duplicate-constraint validators (`Duplicated*Validator` — this repo has a documented history of TOCTOU races here)" as one of the highest-risk areas to test, without distinguishing email from article-title. So CLAUDE.md treats the *pattern* as a known risk category needing test coverage, but does not itself state that the risk is "accepted" as unfixable — the code-level mitigation (unique constraint + exception handler) is what actually makes this instance safe, not an explicit CLAUDE.md waiver.

## Recommendations

No gap requiring a code change was found for `DuplicatedEmailValidator` itself: unlike the article-title validator, its TOCTOU window is closed by a real DB constraint plus a dedicated exception handler, so a lost race produces a correct `422` rather than a duplicate row or an unhandled 500.

One documentation gap: CLAUDE.md's Test Plan section lumps all `Duplicated*Validator` classes together as having "a documented history of TOCTOU races" without noting that email/username races are backed by a DB `UNIQUE` constraint (mitigated) while article-title races are not (unmitigated). Consider updating that paragraph to distinguish the two cases so future readers don't assume `DuplicatedEmailValidator` needs the same fix as the article-title validator.

Per the Test Plan's own risk-scaling guidance, a concurrency-style test asserting that two racing registrations with the same email yield one `201` and one `422` (via `DataIntegrityViolationException`) would be worth adding if not already present, to lock in this mitigation.

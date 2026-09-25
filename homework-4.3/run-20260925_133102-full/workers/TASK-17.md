# Worker Report: TASK-17

## Task
SCOPE: `src/main/java/io/spring/application/user/DuplicatedEmailValidator.java`
QUESTION: Does this validator have the same TOCTOU exposure (check-then-insert, no DB unique constraint backing it) as the article-title duplicate check, and is that a known, accepted risk per CLAUDE.md?

## Findings

- `DuplicatedEmailValidator.isValid` (`DuplicatedEmailValidator.java:14-16`) performs a classic check-then-act: `!userRepository.findByEmail(value).isPresent()` is evaluated at validation time, before the eventual insert. Two concurrent signups with the same email can both pass this check before either commits — the check itself is racy, same shape as the article-title validator's check-then-insert pattern.

- However, the second half of the QUESTION's premise ("no DB unique constraint backing it") does **not** hold for email. `src/main/resources/db/migration/V1__create_tables.sql:5` declares `email varchar(255) UNIQUE`. By contrast, `articles.title` (`V1__create_tables.sql:14`) has no uniqueness constraint at all. So the email validator's race is backed by a real DB constraint, while the article-title race (per CLAUDE.md's Conventions section, "this repo has a documented history of TOCTOU races here") is not — these two are not the same exposure.

- This exact race was already identified and mitigated: `CustomizeExceptionHandler.java:83-99` has a dedicated `@ExceptionHandler(DataIntegrityViolationException.class)` whose inline comment (lines 86-91) explicitly documents the race window between "the application-layer duplicate-username/email check (RegisterParam's validator) and the actual insert," and states the DB-level UNIQUE constraint is "the only thing that catches the second one." It returns a clean `422 UNPROCESSABLE_ENTITY` with `{"message": "username or email already exists"}` — no stack trace, no internal class names, satisfying CLAUDE.md's Security Requirements > Output Encoding and Error Handling ("Never expose stack traces, internal class names, or file paths in API error responses").
  - Note this response shape is the flat `{"message": ...}` form, consistent with CLAUDE.md's "Pitfall — error responses are NOT uniformly shaped" convention for auth/constraint-style errors rather than the field-keyed `{"errors": {...}}` shape.

- CLAUDE.md's Input Validation requirement ("Reject invalid input; do not attempt to sanitize and continue") is satisfied on both the application-validator path (422 via bean validation) and the DB-constraint fallback path (422 via `DataIntegrityViolationException` handler) — the race does not degrade to a 500 or leak an exception.

## Recommendations

No gap found. `DuplicatedEmailValidator` has the same *shape* of check-then-insert race as the article-title validator, but it is a different risk tier: it is backed by a DB `UNIQUE` constraint on `email` (`V1__create_tables.sql:5`, absent for `articles.title`) and has a dedicated, already-implemented safety-net handler (`CustomizeExceptionHandler.java:83-99`) that converts the rare concurrent-duplicate case into a clean 422 rather than an unhandled exception. This is a known, accepted, and already-remediated risk — no further action needed for this validator. (The article-title validator remains the open item per CLAUDE.md's documented history.)

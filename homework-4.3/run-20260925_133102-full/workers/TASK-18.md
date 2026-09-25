# Worker Report: TASK-18

## Task

SCOPE: `src/main/java/io/spring/application/user/DuplicatedUsernameValidator.java`

QUESTION: Does this validator have the same TOCTOU exposure as `DuplicatedEmailValidator`, and is the race-condition safety net (`DataIntegrityViolationException` handling) still in place on both the REST and GraphQL adapters?

## Findings

- **Same shape, same exposure.** `DuplicatedUsernameValidator.isValid` (`src/main/java/io/spring/application/user/DuplicatedUsernameValidator.java:14-16`) does a read-then-decide check — `!userRepository.findByUsername(value).isPresent()` — with no transactional guard between the read and the later insert. `DuplicatedEmailValidator.isValid` (`src/main/java/io/spring/application/user/DuplicatedEmailValidator.java:14-16`) is structurally identical, just checking `findByEmail`. Both are wired onto the same write path via `RegisterParam` (`src/main/java/io/spring/application/user/RegisterParam.java`, referencing `DuplicatedUsernameConstraint`/`DuplicatedEmailConstraint`), so two concurrent registrations can both pass Bean Validation before either commits — a classic TOCTOU race. This matches CLAUDE.md's documented history for `Duplicated*Validator` (Test Plan > Scope & risk section) and is exactly the pattern CLAUDE.md's Input Validation guidance implicitly assumes is backstopped at the DB layer ("Reject invalid input; do not attempt to sanitize and continue" — here the app-layer check alone is not sufficient, so the DB constraint is the actual enforcement mechanism).
- **Safety net is present and shared for both fields, on both adapters.**
  - REST: `CustomizeExceptionHandler.handleDataIntegrityViolation` (`src/main/java/io/spring/api/exception/CustomizeExceptionHandler.java:83-99`) catches `DataIntegrityViolationException` generically (not username- or email-specific) and returns HTTP 422 with `{"message": "username or email already exists"}`. The comment at lines 86-91 explicitly documents this as the safety net for the race window between the validator check and the insert.
  - GraphQL: `GraphQLCustomizeExceptionHandler.onException` (`src/main/java/io/spring/graphql/exception/GraphQLCustomizeExceptionHandler.java:66-77`) has the matching `else if (... instanceof DataIntegrityViolationException)` branch, returning a `BAD_REQUEST` GraphQL error with the same message. Comment at lines 67-70 cross-references the REST handler as the same mechanism.
  - Both handlers rely on the DB-level `UNIQUE` constraint on `users.username`/`users.email` (per `V1__create_tables.sql`, referenced in both handlers' comments) as the actual point of enforcement — the validator is best-effort UX, the constraint + exception handler is the correctness guarantee.
- No gap specific to username vs. email: the handler in both adapters is exception-type-based, not field-based, so it covers a `DataIntegrityViolationException` triggered by either the username or the email unique constraint equally.

## Recommendations

No gap found. `DuplicatedUsernameValidator` has the identical (expected/accepted) TOCTOU exposure as `DuplicatedEmailValidator`, and both REST (`CustomizeExceptionHandler.java:83`) and GraphQL (`GraphQLCustomizeExceptionHandler.java:66`) safety nets are in place, generic enough to cover both fields, and consistent with each other. No code change is needed for this validator; if anything, the existing inline comments in both exception handlers already capture this reasoning for future readers, so no further documentation is needed either.

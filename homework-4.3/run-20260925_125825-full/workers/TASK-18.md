# Worker Report: TASK-18

## Task
SCOPE: `src/main/java/io/spring/application/user/DuplicatedUsernameValidator.java`
QUESTION: Does this validator have the same TOCTOU exposure as `DuplicatedEmailValidator`, and is the race-condition safety net (`DataIntegrityViolationException` handling) still in place on both the REST and GraphQL adapters?

## Findings

**1. Same TOCTOU shape as `DuplicatedEmailValidator`.**
`DuplicatedUsernameValidator.isValid` (`src/main/java/io/spring/application/user/DuplicatedUsernameValidator.java:14-16`) does a plain check-then-act read:

```java
return (value == null || value.isEmpty()) || !userRepository.findByUsername(value).isPresent();
```

This is structurally identical to `DuplicatedEmailValidator.isValid` (`src/main/java/io/spring/application/user/DuplicatedEmailValidator.java:14-16`), which does the same thing via `userRepository.findByEmail(value)`. Neither method holds any lock or transaction spanning the check and the later insert — two concurrent signups with the same username (or email) can both pass `isValid` before either commits its `INSERT`. This is exactly the class of input-validation gap CLAUDE.md's Security Requirements > Input Validation section warns about ("Reject invalid input; do not attempt to sanitize and continue" implies the validation must actually be authoritative — here it is advisory only, not authoritative, since it can't see uncommitted concurrent writes).

**2. The race is not closed at the validator level — it relies on a DB-level backstop.**
`src/main/resources/db/migration/V1__create_tables.sql:3` and `:5` declare `username varchar(255) UNIQUE` and `email varchar(255) UNIQUE`, so the actual duplicate-prevention guarantee comes from the database constraint, not from `DuplicatedUsernameValidator`/`DuplicatedEmailValidator`. This is a reasonable design (matches the documented history of TOCTOU races referenced in CLAUDE.md's Test Plan section) provided the resulting `DataIntegrityViolationException` is always caught and turned into a clean error — see below.

**3. The safety net is in place on both adapters, and is explicitly documented as such.**
- REST: `src/main/java/io/spring/api/exception/CustomizeExceptionHandler.java:83-94` — `@ExceptionHandler(DataIntegrityViolationException.class)` returns `422 UNPROCESSABLE_ENTITY` with a clean body. The comment at lines 86-91 explicitly names this as the safety net "for the race window between the application-layer duplicate-username/email check ... and the actual insert," citing `V1__create_tables.sql`'s UNIQUE constraints.
- GraphQL: `src/main/java/io/spring/graphql/exception/GraphQLCustomizeExceptionHandler.java:66-74` — an `else if (handlerParameters.getException() instanceof DataIntegrityViolationException)` branch returns a `BAD_REQUEST`-typed `GraphQLError` with message `"username or email already exists"`. The comment at lines 67-70 explicitly calls this "Same race-window safety net as CustomizeExceptionHandler (REST)."

Both handlers are generic (they key off `DataIntegrityViolationException`, not a username-specific or email-specific exception type), so they cover the username race just as much as the email race — there's nothing username-specific missing here.

## Recommendations

No gap found. `DuplicatedUsernameValidator` has the same (expected, already-mitigated) TOCTOU exposure as `DuplicatedEmailValidator`, and both the REST (`CustomizeExceptionHandler.java:83`) and GraphQL (`GraphQLCustomizeExceptionHandler.java:66`) adapters still have the `DataIntegrityViolationException` safety net wired up, with comments in both places explicitly tying them to this exact race condition. No action needed. If anything, consider adding a regression/integration test (per CLAUDE.md's Test Plan, this validator area is flagged as historically race-prone) that concurrently submits two identical usernames against the real endpoint and asserts a clean 422/BAD_REQUEST rather than a 500 — but this is a coverage suggestion, not a defect.

# Worker Report: TASK-15

## Task
SCOPE: src/main/java/io/spring/application/user/RegisterParam.java
QUESTION: Does this DTO's Bean Validation annotations enforce required fields, email format, and length bounds on username/email/password consistent with the underlying database column widths?

## Findings

**Required fields — enforced.**
- `email`: `@NotBlank(message = "can't be empty")` at RegisterParam.java:15
- `username`: `@NotBlank(message = "can't be empty")` at RegisterParam.java:20
- `password`: `@NotBlank(message = "can't be empty")` at RegisterParam.java:24

**Email format — enforced.**
- `email`: `@Email(message = "should be an email")` at RegisterParam.java:16

**Length bounds — NOT enforced (gap).**
- None of the three fields (`email` RegisterParam.java:15-18, `username` RegisterParam.java:20-22, `password` RegisterParam.java:24-25) carry a `@Size` (or `@Length`) constraint.
- The underlying `users` table defines all three as `varchar(255)`: `id varchar(255) primary key, username varchar(255) UNIQUE, password varchar(255), email varchar(255) UNIQUE` (src/main/resources/db/migration/V1__create_tables.sql:2-5).
- This violates CLAUDE.md's Security Requirements > Input Validation: "add `@Size` bounds on any new user-supplied string field." `RegisterParam` is exactly this kind of user-supplied string field DTO (registration request body) and has no upper bound on any field.
- Because the datastore is SQLite, the `varchar(255)` column width is a documentation-only hint — SQLite uses dynamic typing and does not enforce or truncate at 255 characters (this differs from e.g. MySQL/Postgres, where an overlong insert would either truncate or raise a data-length error). So an oversized `username`/`email`/`password` (e.g. tens of thousands of characters) will be accepted by validation and persisted in full, silently growing past the column's intended width. This is a genuine consistency gap between the DTO's declared bounds (none) and the schema's declared bounds (255), not merely a defense-in-depth nice-to-have.
- Secondary boundary-condition gap: without `@Size(min=1)`/lower bound guidance, a `password` of length 1 (or any length) passes `@NotBlank` — `@NotBlank` only rejects blank/whitespace-only strings, not weak/short passwords. (Not a DB-width issue, but relevant to "boundary" case coverage called out in CLAUDE.md's Test Plan.)

## Recommendations

Add explicit `@Size` upper bounds to all three fields to match the `varchar(255)` schema and satisfy the CLAUDE.md Input Validation requirement, e.g.:

```java
@NotBlank(message = "can't be empty")
@Email(message = "should be an email")
@Size(max = 255, message = "is too long")
@DuplicatedEmailConstraint
private String email;

@NotBlank(message = "can't be empty")
@Size(max = 255, message = "is too long")
@DuplicatedUsernameConstraint
private String username;

@NotBlank(message = "can't be empty")
@Size(max = 255, message = "is too long")
private String password;
```

Since SQLite will not itself reject or truncate an oversized value, `@Size(max = 255)` here is not defense-in-depth — it is the only enforcement point of the schema's intended width. Add corresponding boundary test cases (254/255/256-character values) per CLAUDE.md's Test Plan case-coverage guidance, and a test asserting rejection of an over-limit field with the `{"errors": {"field": ["msg"]}}` shape from `CustomizeExceptionHandler`.

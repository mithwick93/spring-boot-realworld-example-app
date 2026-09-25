# Worker Report: TASK-19

## Task
SCOPE: `src/main/java/io/spring/application/Page.java`
QUESTION: Does this class validate or clamp offset/limit values, and if it clamps silently instead of rejecting invalid input, is that consistent with CLAUDE.md's Input Validation section?

## Findings
- `Page.java:9-10` declares defaults `offset = 0`, `limit = 20`, with no bounds annotations (no `@Min`/`@Max`/`@Positive` etc.).
- `Page.java:12-15` — the public constructor `Page(int offset, int limit)` just calls the two private setters with the raw arguments, no branching or transformation.
- `Page.java:17-23` — `setOffset` and `setLimit` are trivial pass-through assignments (`this.offset = offset;` / `this.limit = limit;`). There is no clamping (e.g. `Math.max(0, ...)` or capping `limit` to a max page size) and no validation (no thrown exception, no rejection) anywhere in the class.

So the class does **neither** validation **nor** clamping — it is a pure data holder that accepts whatever `int` values are passed in, including negative offsets, negative limits, zero, or arbitrarily large limits (e.g. `Integer.MAX_VALUE`). The private setters give the appearance of a validation seam (via Lombok's `@Data` normally generating public setters, but these are hand-written and made `private`) yet contain no actual logic beyond straight assignment.

This matters because `Page` is constructed from user-controlled query parameters in the REST layer (e.g. `offset`/`limit` request params bound to controller method args and passed into `Page`), making it external, untrusted input per CLAUDE.md's Security Requirements. The Input Validation section states: "Treat ALL external input as untrusted (request bodies, query params, file contents)" and "Reject invalid input; do not attempt to sanitize and continue." `Page.java` does neither — it neither rejects nor sanitizes/clamps; it silently accepts invalid values unchanged. Since there's no clamping at all here, the specific "silent clamping" question doesn't apply to this file — the actual gap is a complete absence of validation, which is a more direct violation of "Reject invalid input" than silent clamping would be.

Practical impact: a negative `limit` or `offset` passed through to a MyBatis `LIMIT #{limit} OFFSET #{offset}` style query would either produce a SQL error (information leak risk per the Output Encoding section, if the driver surfaces it) or undefined/unexpected result-set behavior; a very large `limit` allows unbounded result-set retrieval (resource exhaustion / a `@Size`-style bound is explicitly called for by CLAUDE.md: "add `@Size` bounds on any new user-supplied string field" — the same spirit applies to numeric bounds on paging params, though the doc's literal wording is scoped to strings).

## Recommendations
- Add Bean Validation constraints or explicit checks on `offset` (`>= 0`) and `limit` (`> 0` and `<= <some max page size>`, e.g. 100) at the point where user input is bound into `Page` — either via `@Min`/`@Max` on the controller's `@RequestParam` fields, or by having `setOffset`/`setLimit` in `Page.java` throw/reject out-of-range values (e.g. `IllegalArgumentException` mapped to a client error, consistent with "Reject invalid input; do not attempt to sanitize and continue").
- If the intent is instead to clamp (e.g. cap `limit` at a max page size rather than rejecting), that should be a deliberate, documented choice — and per CLAUDE.md, rejecting is the stated policy, so clamping alone (without at minimum rejecting negative/zero values) would still not fully satisfy the Input Validation section.
- This file itself contains no test coverage implied by its current form; per CLAUDE.md's Test Plan, `io.spring.application.*` pure classes should have unit tests — once bounds are added, cover boundary cases (negative, zero, exact max, one past max) as required by the "Case coverage" rule.

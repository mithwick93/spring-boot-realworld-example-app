# Worker Report: TASK-20

## Task
**Scope:** `src/main/java/io/spring/application/CursorPageParameter.java`
**Question:** Does this class validate its cursor and limit inputs before use, and what happens on a malformed or out-of-range cursor?

## Findings

**`limit` — partial validation, silent coercion, no rejection**
- `setLimit` (CursorPageParameter.java:33-39) only handles the upper bound and the "normal" case:
  - `limit > MAX_LIMIT` (1000) → clamps to 1000 (line 34-35).
  - `limit > 0` → accepted as-is (line 36-37).
  - Any other value — `0`, negative, `Integer.MIN_VALUE` — falls through **both** branches and is silently ignored. The field keeps its default-initializer value `20` (line 11), set before the constructor body runs.
- There is no `else` branch that rejects or errors on an invalid limit (e.g. negative page size). The caller receives a "successful" `CursorPageParameter` with a substituted value and no indication their input was invalid.
- This directly conflicts with CLAUDE.md's Security Requirements → Input Validation: *"Reject invalid input; do not attempt to sanitize and continue."* Here, out-of-range/negative `limit` is silently sanitized (clamped or defaulted) rather than rejected.
- No `javax.validation` annotations (`@Min`, `@Max`, `@Positive`, etc.) are present anywhere in the class — validation is done with hand-rolled `if` logic in a private setter instead of the Bean Validation convention CLAUDE.md documents for DTOs (see "Conventions" → Validation section), so this class also doesn't get automatic `MethodArgumentNotValidException` → `{"errors": {...}}` handling via `CustomizeExceptionHandler`.

**`cursor` — no validation at all**
- `setCursor` (CursorPageParameter.java:29-31) is a pure passthrough: `this.cursor = cursor;`. No null-check, no type/format check, no range check, and — because `T` is a generic type — no way for this class to know what "malformed" even means for the concrete cursor type.
- The class performs zero validation of the cursor value itself. Any parsing/format-checking of the raw client-supplied cursor string happens **before** this class is constructed, in the caller. For example, `DateTimeCursor.parse(String)` (`src/main/java/io/spring/application/DateTimeCursor.java:17-22`) does `Long.parseLong(cursor)` with no try/catch — a malformed cursor string (non-numeric, empty, oversized) throws an uncaught `NumberFormatException` *before* `CursorPageParameter`'s constructor is ever reached. By the time `CursorPageParameter(T cursor, ...)` (line 15-19) runs, `cursor` is already a parsed, "valid-looking" `DateTime`/other object — there is no defensive re-check inside this class if a caller passes a raw/unvalidated value directly.
- Practical effect of a malformed cursor: an exception is thrown upstream of this file (in the parse step), not handled here, and not sanitized to a generic client-safe message by this class. Whether that exception is caught and given a generic message downstream is outside this class's scope and wasn't verified here — but this class provides no defense-in-depth (e.g., no null-check, no `@AssertTrue`/custom validator) if the cursor object itself is out of expected range once it is set.

## Recommendations

1. **`limit`:** Replace the silent-default branch in `setLimit` (CursorPageParameter.java:33-39) with an explicit rejection path (e.g. throw `IllegalArgumentException`/a validation exception, or add `@Min(1)`/`@Max(1000)` if this were promoted to a `@Valid`-annotated request DTO) for `limit <= 0`, instead of quietly falling back to `20`. Silent coercion hides caller bugs and violates the "reject, don't sanitize" rule in CLAUDE.md's Input Validation section.
2. **`cursor`:** Add a null-check / format assertion in `setCursor` (or require callers to pass an already-validated `PageCursor<T>` wrapper) so this class doesn't unconditionally trust its generic `cursor` argument. At minimum, document that cursor parsing/validation is the caller's responsibility (e.g. point to `DateTimeCursor.parse`), since nothing in `CursorPageParameter` itself enforces it.
3. Consider whether a malformed cursor's `NumberFormatException` (thrown in `DateTimeCursor.parse`, upstream of this class) is actually caught by a REST/GraphQL exception handler and turned into a generic client-safe error — this wasn't in scope for this file, but it's the direct consequence of this class doing no cursor validation of its own, and should be checked (CLAUDE.md: "Never expose stack traces... in API error responses").

# Worker Report: TASK-20

## Task
SCOPE: src/main/java/io/spring/application/CursorPageParameter.java
QUESTION: Does this class validate its cursor and limit inputs before use, and what happens on a malformed or out-of-range cursor?

## Findings

**`limit` — partially bounded, no rejection of invalid input.**
- `setLimit` (CursorPageParameter.java:33-39) clamps the upper bound: any `limit > MAX_LIMIT` (1000, line 10) is silently reduced to 1000 (line 34-35).
- Any `limit <= 0` (including 0 and negative values) is silently ignored and the field simply keeps its default of `20` (line 11), because the `else if (limit > 0)` branch (line 36-37) is the only path that overwrites the default.
- There is no rejection path at all — out-of-range input is *sanitized to a fallback value* rather than rejected, which is the opposite of what CLAUDE.md's Security Requirements > Input Validation section requires: "Reject invalid input; do not attempt to sanitize and continue." No exception, no error, no logging occurs for a caller-supplied `limit=-5` or `limit=0`.
- `getQueryLimit()` (line 25-27) returns `limit + 1` — with `limit` already clamped to `[1, 1000]` in practice (or defaulted to 20 for non-positive input), this is bounded correctly for the SQL query, so there's no unbounded-limit risk here.

**`cursor` — no validation whatsoever.**
- `setCursor` (line 29-31) is a bare, unconditional assignment: `this.cursor = cursor;`. There is no null check, no format/type check, no bounds check.
- The field is generic (`T cursor`, line 12), so this class has no way to know what "malformed" even means for the concrete type in use (e.g. a numeric ID string for `CommentQueryService`/`CommentReadService`, or a DateTime-based cursor for `ArticleQueryService`/`ArticleReadService` — see grep of callers). Validation of cursor shape is therefore not just missing here, it's structurally deferred to whatever code consumes `getCursor()` downstream (the MyBatis read services), and this class provides no contract or guard to catch a bad value before it gets there.
- Because there's no Bean Validation annotation (`@NotNull`, `@Pattern`, etc.) on `cursor`, and this class isn't itself a `@Valid @RequestBody` DTO validated by Spring, a malformed cursor (wrong type, unparseable string, id referencing nothing) passes straight through unchanged. Per CLAUDE.md: "Treat ALL external input as untrusted... Validate format and required fields via Bean Validation" — this field has none of that.

**What actually happens on a malformed/out-of-range cursor**, based on this file alone: nothing here catches it. The value is stored as-is by `setCursor` and returned as-is by the generated `@Data` getter, to be handed to a MyBatis mapper query (in `ArticleReadService`/`CommentReadService`, outside this file's scope) with `#{cursor}` parameter binding. A malformed value (wrong format/type) would surface only when MyBatis attempts to bind/convert it or when the SQL comparison runs — i.e., as a downstream type-conversion or SQL error, not as a clean, intentional validation rejection at the boundary. A well-formed but out-of-range cursor (e.g., an ID beyond any real row) would simply match zero rows and produce an empty page — not a validation error, since nothing here checks range against real data either.

## Recommendations

1. Add explicit rejection (not silent clamping) for non-positive `limit`: throw an `IllegalArgumentException` (or a dedicated exception mapped by `CustomizeExceptionHandler`) for `limit <= 0`, instead of silently falling back to the default of 20. Silent sanitization hides caller bugs and contradicts the "reject, don't sanitize" rule in CLAUDE.md's Input Validation section.
2. Add a null/format check in `setCursor`, or better, validate the cursor at the API/GraphQL entry point (via Bean Validation on the request DTO, e.g. `@Pattern` for string-typed cursors or a custom validator) before it ever reaches `CursorPageParameter`, so malformed cursors are rejected with a proper 4xx response instead of failing deeper in the MyBatis layer with a less predictable error (and per CLAUDE.md's Output Encoding section, any resulting error must not leak stack traces/internal class names to the client — an uncaught type-conversion failure risks doing exactly that).
3. Given `cursor` is generic (`T`), the validation likely can't live in this shared class meaningfully — recommend adding it in each concrete caller (`ArticleQueryService`, `CommentQueryService`, or their GraphQL/REST entry points) where the concrete cursor type/format is known.

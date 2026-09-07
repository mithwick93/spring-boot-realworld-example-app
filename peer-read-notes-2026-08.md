# Peer-Read Notes: spec-2026-08.md (POST /articles/{slug}/report)

The peer saw only the finished `spec-2026-08.md` document — no design notes, no rationale, no repo access — and was asked what they'd build and what was ambiguous. Full response below, with my own notes on which findings held up.

## What the peer produced (full response, verbatim)

> ## What I'd build
>
> **Controller** — `ArticleReportApi`, `@RestController`, mapped to `POST /articles/{slug}/report`. Signature roughly:
>
> ```java
> @PostMapping("/articles/{slug}/report")
> public ResponseEntity<ReportResponse> report(
>     @PathVariable String slug,
>     @Valid @RequestBody ReportParam reportParam,
>     @CurrentUser User currentUser) {
>   Article article = articleQueryService.findBySlug(slug)
>       .orElseThrow(() -> new ResourceNotFoundException("article", slug));
>   articleReportService.report(article.getId(), currentUser.getId(), reportParam.getReason());
>   return ResponseEntity.status(201).body(new ReportResponse(slug, reportParam.getReason(), Instant.now()));
> }
> ```
>
> I'd reuse whatever existing `@CurrentUser`/argument-resolver mechanism already extracts the JWT principal on the other article endpoints, rather than invent a new one — same for the article-lookup call, which I'd expect already exists as a service method used by `ArticleFavoriteApi`.
>
> **Request DTO**: `ReportParam` with `@JsonRootName("report")`, single field `reason` annotated `@NotBlank`.
>
> **Response DTO**: `ReportResponse` wrapped the same way, fields `articleSlug`, `reason`, `createdAt` (Instant, serialized ISO-8601 with millis and `Z`, matching however `createdAt` is already serialized on `Article`).
>
> **Service/repository**: `ArticleReportService.report(articleId, userId, reason)` calls a MyBatis mapper `insert`. On the resulting composite-PK violation, I'd catch Spring's translated `DuplicateKeyException` (not a vendor-specific SQL error code, so it survives whatever DB the test suite/prod actually run — H2 in tests, presumably Postgres/MySQL in prod) and rethrow as a new `DuplicateReportException`, registered in `CustomizeExceptionHandler` to produce `422 { "message": "article already reported" }`.
>
> **Migration** `V2__create_article_report_table.sql` — exactly the table given in the spec (composite PK on `article_id, user_id`, `reason varchar(1000)`, `created_at timestamp`).
>
> **Error behavior**:
> - No/invalid JWT → security filter rejects with bare 401 before the controller runs — no code change needed beyond making sure this path is covered by whatever matcher in `WebSecurityConfig` currently protects the other article-mutation endpoints.
> - Bad slug → existing `ResourceNotFoundException` → bare 404 (whatever mechanism already produces that for `ArticleFavoriteApi`, reused as-is).
> - Blank/missing `reason` → Bean Validation → existing `{"errors": {"reason": ["can't be blank"]}}` shape, no new handler needed.
> - Duplicate report → new `DuplicateReportException` → `422 {"message": "article already reported"}`.
> - Success → `201` with the report envelope.
>
> ## Ambiguities / things I had to assume
>
> 1. **How the current user is resolved** — I assumed there's an existing `@CurrentUser`-style resolver reused from other endpoints; the spec never shows it.
> 2. **Timestamp timezone** — the DB column is a plain `timestamp` (no tz), but the response shows a `Z`-suffixed UTC instant. I assumed the app always writes/reads in UTC consistently with however `Article.createdAt` already does it; nothing in the spec confirms this.
> 3. **`reason` length vs. column width** — validation is `@NotBlank` only, "no length cap," but the column is `varchar(1000)`. What happens if someone submits 5,000 characters? Spec doesn't say. I assumed this is a gap, not an intentional "let the DB reject it."
> 4. **Malformed/missing envelope** (e.g. `{"reason": "..."}` without the `report` wrapper) isn't covered by any AC — I assumed it falls back to whatever Jackson/`@JsonRootName` error behavior already exists for `NewArticleParam`, i.e. not a new decision to make.
> 5. **Whether the security config is an allow-list or default-secure** — FR7/AC5 assume the new path is already covered by "existing JWT bearer-token auth," but if `WebSecurityConfig` enumerates specific authenticated paths, this new path has to be explicitly added. Spec treats this as zero-effort reuse; in practice it's at least a one-line config change I'd need to verify.
> 6. **Whether `DuplicateKeyException` translation actually fires the same way** across whatever DB is used in tests (H2) vs. prod — the spec calls this out as an open question and I picked "catch the DB exception directly" over "check-then-insert," but I'd want an integration test (via `DbTestBase`) that does two real inserts and confirms the exception path, not a mocked repository test, since this is exactly the kind of thing that looks fine mocked and breaks against a real driver.
>
> ## What I'd build differently / gaps I'd flag
>
> - **Add `@Size(max = 1000)` on `reason`** to mirror the column width. As written, an over-length reason produces an unhandled `DataIntegrityViolationException` → likely a generic 500, which is worse than a clean 422.
> - **409 vs 422 for duplicates**: I'd have reached for `409 Conflict` as the more RESTful status for "this resource already exists," but the spec is explicit about matching the existing `{"message": ...}` precedent at 422, so I'd follow that rather than substitute my own judgment — just noting the friction.
> - **No visibility into reports at all is a real operational gap, not just a scoping choice.** Everything written to `article_report` is currently unreachable — no GET, no list, no admin view, not even a report `id` in the response to correlate later. I'd flag to whoever owns the roadmap that this feature currently writes to a black hole; at minimum I'd want a follow-up ticket for an admin-facing read path before this ships to real users, and in the meantime I'd add a log line on successful report creation so ops can at least `grep` for activity.
> - **Table has no FK constraints** to `articles`/`users` (matching the stated `article_favorites` pattern) — consistent with the spec's instruction, but worth a raised eyebrow since it means an orphaned `article_id`/`user_id` is possible if those rows are ever hard-deleted elsewhere. Not fixing this unilaterally since it explicitly mirrors an existing pattern, but I'd mention it.
> - **No surrogate `id` on the report row** — fine given there's no plan to view/retract individual reports (the composite key already uniquely addresses one), but if a "retract my report" endpoint is added later, `DELETE /articles/{slug}/report` keyed off the current user is still trivial with this schema, so I wouldn't add an id preemptively — just flagging that I considered it and decided against, per the given schema.

## Assessment against the ~80% test

The peer's reconstruction matches my intent closely — controller shape, request/response envelopes, all five error paths (404, 401, blank-reason 422, duplicate 422, success 201), the migration table, and even the exact reasoning for choosing `DuplicateKeyException` translation over check-then-insert (the same open question I left unresolved in Constraints). Nobody invented a different feature; the core behavior came back correctly from the document alone.

Where it diverged is informative rather than concerning:

- It guessed a `@CurrentUser` annotation name that doesn't match this repo's actual convention (`@AuthenticationPrincipal User user`) — a plausible invention where the spec deliberately left an implementation detail unspecified. Not a comprehension failure; the spec isn't supposed to dictate Spring wiring.
- It raised the `WebSecurityConfig` allow-list-vs-default-secure question, which the spec states as a given ("must reuse the existing JWT bearer-token auth") without confirming which config style is in play. Worth a note for whoever implements task 1 in 2.3, but not a spec defect — this is exactly the kind of thing a real four-phase Plan step should verify against the actual file.
- It found a real defect: **the spec said `reason` had "no length cap" while also defining the column as `varchar(1000)`** — a genuine internal inconsistency that would have produced an unhandled 500 on an over-length submission instead of a clean validation error. This is precisely the kind of gap a peer read exists to catch, and it's now fixed in `spec-2026-08.md`: `reason` carries `@Size(max = 1000)`, and a new **AC7** covers the over-length case with the same Bean Validation error shape as the blank-reason case.

## Invariant (per the Evaluate prompt)

Picking AC2 (duplicate report) as the error-case acceptance criterion: for any request where the GIVEN is "this user has already reported this article," no matter what the WHEN varies (same reason text, different reason text, retried immediately or after a delay), the state invariant is that exactly one `article_report` row exists for that `(article_id, user_id)` pair, and its `reason`/`created_at` values are the ones from the *first* successful report, never overwritten by a later attempt.

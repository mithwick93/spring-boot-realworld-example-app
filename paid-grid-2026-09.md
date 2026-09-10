# PAID Grid — Cursor Pagination Utility

Module: `io/spring/application/{CursorPager, CursorPageParameter, PageCursor, DateTimeCursor, Node}.java`

## Debt Inventory

Severity rated 1–5 across five dimensions. Engage decision per item (Refactor / Wrap / Rewrite / Delete / Tolerate).

| # | Item | Complexity/Coupling | Test Coverage/Quality | Documentation | Recent Bug Burden | Risk | Decision |
|---|---|---|---|---|---|---|---|
| 1 | Dead `Collections.reverse()` call (`ArticleQueryService.java:70`) — never affects output, `ArticleReadService.xml:91` forces `ORDER BY A.created_at DESC` regardless of input order | 3 | 4 | 4 | 3 | 4 | Refactor |
| 2 | Millisecond-resolution cursor timestamps, no secondary sort key (`DateTimeCursor.java:12-22`, `ArticleReadService.xml:119-131`) — same-millisecond ties at a page boundary can skip or duplicate a row | 3 | 5 | 5 | 2 | 4 | Refactor |
| 3 | No compiler-enforced link between a read-model's cursor type and a query parameter's declared type (`PageCursor.java:3` abstract, `DateTimeCursor.java:6` its only subclass) | 3 | 5 | 5 | 1 | 2 | Tolerate — risk and bug burden are already low with only one subclass ever created; hardening the generics now is speculative for a mismatch that doesn't currently exist |
| 4 | Raw-typed `CursorPageParameter` in `ArticleReadService.java:32-33,37-41` vs. parameterized `CursorPageParameter<DateTime>` in `CommentReadService.java:16-17` | 2 | 5 | 4 | 1 | 2 | Refactor |
| 5 | Zero dedicated unit tests across all five files in the module; only shallow, incidental coverage from higher-level tests that assert list size, not ordering or boundary correctness | 2 | 5 | 4 | 3 | 4 | Refactor |
| 6 | Asymmetric limit validation in `CursorPageParameter.setLimit` (`CursorPageParameter.java:33-39`) — an over-limit value is explicitly clamped, but a zero or negative value is silently accepted with no floor clamp, exception, or log; the sane-looking result relies on field-initializer order, not an explicit rule | 2 | 5 | 5 | 1 | 2 | Refactor |

## PAID 2×2

Severity = average of the five dimensions above (high ≥ 3, low < 3). Business value scored 1–5 by a stakeholder review independent of the severity scores.

|  | **High business value** | **Low business value** |
|---|---|---|
| **Low severity** | Address: (none) | Document: #4 |
| **High severity** | Prioritize: #5, #6 | Investigate: #1, #2 |

Item #3 is excluded from the grid — Tolerated, not engaged.

## Fowler Tags — Top 3

Top 3 by PAID priority: #5, #6, #2.

- **#5 Zero test coverage** — Reckless, Inadvertent. A gap that accrued as the module grew, not a conscious scope cut.
- **#6 Asymmetric limit validation** — Reckless, Inadvertent. A missed edge case, not a deliberate tradeoff.
- **#2 Millisecond-cursor collision risk** — Reckless, Inadvertent. An implicit uniqueness assumption baked in without anyone deciding it.

No reordering resulted from the Fowler check — all three top items land in the same quadrant of Fowler's lens, reinforcing rather than revising the PAID placement.

## Technical Debt Ratio

- Remediation estimate (all six identified items, including #3): **23.5 hours**
- Rebuild-from-scratch estimate: **28 hours**
- TDR = 23.5 / 28 ≈ **84%**

Well above the 5% benchmark. Descoping #3's generics work (4h, already Tolerated) and scoping #5's test suite to just the two live-risk paths (#2 and #6) rather than full coverage of all five classes would cut remediation to roughly 12–13 hours — though even then, this ratio describes one small isolated utility, not a whole codebase, which is what the 5% benchmark is calibrated against.

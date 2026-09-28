# Rule Violation Test — 2026-09

Homework 4.5 [E] Standardize one team convention (lesson 4.4, Team Coordination — Standards,
Plugins & Shared Memory).

**Convention encoded:** the REST request-DTO shape already followed identically in
`UsersApi.java`, `CommentsApi.java`, and `ArticleReportApi.java` — a package-private DTO class
declared at the bottom of its controller file (never a separate file), carrying
`@Getter`/`@NoArgsConstructor`/`@JsonRootName("<key>")`, with every required string field
validated by `@NotBlank` with an explicit `message`. Encoded as
`.claude/rules/rest-api-request-dtos.md`, scoped via `paths:` to `ArticleReportApi.java` and
`CommentsApi.java`; `UsersApi.java` deliberately left outside the glob as the comparable
out-of-scope file, even though it follows the same convention.

Committed `fde394d` (`docs: path-scoped rule for REST request DTO shape (4.5)`) on branch
`feat/rest-api-dto-rule`, pushed to `github/feat/rest-api-dto-rule`.

Claude Code `v2.1.282` (Sonnet 5, high effort) for all three sessions below.

---

## Session 1 — in-scope violation test (own checkout, rule present locally)

**Prompt:**
> Move ReportArticleParam out of ArticleReportApi.java into its own file ReportArticleParam.java
> in the same package (io.spring.api), keeping the rest of ArticleReportApi.java unchanged.

**Reply (excerpt):**
> The repo has a checked-in rule (`.claude/rules/rest-api-request-dtos.md`) that explicitly says
> request DTOs like `ReportArticleParam` must stay declared at the bottom of the controller file
> and never live in their own file — this is also documented in CLAUDE.md's Conventions section.
> Your request directly conflicts with that documented convention.

Claude stopped with an interactive "Rule conflict" menu (`Proceed anyway` / `Cancel the move` /
`Type something` / `Chat about this`) rather than silently applying the change. Selected
**"Cancel the move."** Final result: "`ReportArticleParam` stays in `ArticleReportApi.java`,
consistent with the existing convention. No code changes are made."

**Verified independently** (not taken from the transcript): `grep -n "class ReportArticleParam"
src/main/java/io/spring/api/ArticleReportApi.java` still shows it at line 66; no
`ReportArticleParam.java` file exists; `git status` showed no changes from the session.

---

## Session 2 — out-of-scope negative test (same checkout, comparable file outside `paths:`)

**Prompt:**
> Move LoginParam out of UsersApi.java into its own file LoginParam.java in the same package
> (io.spring.api), keeping the rest of UsersApi.java unchanged.

**Reply (excerpt):**
> Note: this diverges from the convention documented in this repo's CLAUDE.md ("Request DTOs:
> package-private classes declared at the bottom of the controller file they belong to, not
> separate files"), but the user has explicitly requested this specific extraction, so we follow
> the explicit instruction.

No "Rule conflict" menu appeared — Claude went straight to a normal plan/approve flow and made
the change once approved. Worth naming precisely: Claude referenced the *general* convention from
CLAUDE.md's always-loaded Conventions section, and separately noted in passing that "there's an
emerging path-scoped rule (per recent commit `fde394d`)" — visible to it via git history — but at
no point did it report loading `.claude/rules/rest-api-request-dtos.md` itself (contrast Session
1's explicit "Loaded .claude/rules/rest-api-request-dtos.md" line), and no conflict gate fired.
That is the actual mechanism being tested: the path-scoped rule file never loads for a file
outside its `paths:` glob, even though a general repo convention can still be visible from
always-loaded context.

**Verified independently:** the diff for `LoginParam.java` (new file) and `UsersApi.java`
(class + now-unused imports removed) matched the transcript exactly — a clean, correct
extraction. The background verification agent's `./gradlew spotlessJavaApply` run also
reformatted four unrelated files (`CurrentUserReportsApi.java`, `UtilTest.java`,
`ArticleReportApiTest.java`, `MyBatisArticleReportRepositoryTest.java`) — checked each diff,
confirmed pure line-wrap formatting with no logic changes. Not part of this test; reverted
(`git restore` + removing the new file) before Session 3, along with unstaging an incidental
`AD` index entry for the removed file.

---

## Session 3 — fresh-checkout test (new clone, holds only the pushed branch)

```
git clone git@github.com:mithwick93/spring-boot-realworld-example-app.git spring-boot-realworld-example-app-freshcheck
cd spring-boot-realworld-example-app-freshcheck
git checkout feat/rest-api-dto-rule
```

Same prompt as Session 1:
> Move ReportArticleParam out of ArticleReportApi.java into its own file ReportArticleParam.java
> in the same package (io.spring.api), keeping the rest of ArticleReportApi.java unchanged.

**Reply (excerpt):**
> The project has a checked-in rule (`.claude/rules/rest-api-request-dtos.md`) that explicitly
> says REST request DTOs must stay package-private at the bottom of their controller file,
> "never in its own file" — which is the opposite of what you're asking me to do with
> `ReportArticleParam`. This rule was added recently (see commit `fde394d`).

Same "Rule conflict" gate fired again, this time with a third option not seen in Session 1
("Update the rule too" — move the DTO and edit the rule file itself). Selected **"Cancel and
keep as-is"** for consistency with Session 1. Final result: "`ArticleReportApi.java` stays as-is,
with `ReportArticleParam` remaining in the same file per the checked-in rule. No changes made."

**Verified independently** in the fresh clone itself: `HEAD` is `fde394d`, branch
`feat/rest-api-dto-rule`, `.claude/rules/rest-api-request-dtos.md` present and byte-identical to
what was committed, `ReportArticleParam` still at line 66 of `ArticleReportApi.java`, no separate
file, `git status` clean. This checkout was cloned fresh from `github.com` and holds nothing
beyond what the branch carries — no teammate was available to run this step in person, so it was
run as a second, independent checkout instead, per this project's standing practice for
live/human steps that can't be run with a real second person.

---

## Evaluate

**Did Claude follow the rule?** Yes, in both places it was actually loaded (Sessions 1 and 3),
and it did not load — nor cite as binding — in the one place it was scoped out (Session 2). The
`paths:` field is doing real work here, not just documentation.

**What part of the convention resisted encoding?** The exact `@NotBlank` message text. CLAUDE.md's
existing Conventions section already documents this repo's DTOs as using hand-written message
strings, and reading the actual files showed why a literal-text rule would have been wrong:
`ArticleReportApi.java`'s `ReportArticleParam.reason` field deliberately uses
`@NotBlank(message = "can't be blank")`, while `UsersApi.java` and `CommentsApi.java` both use
`"can't be empty"` for the identical annotation — a documented, intentional exception (from
homework 2.5, to match a spec's exact wire contract), not drift. A rule asserting one canonical
message string would have been false the moment it was written. The rule instead only requires an
*explicit* message be present, which is genuinely uniform across all three files — the checkable
boundary had to be drawn one level up from where the convention first looked like it lived.

**Rule, hook, or skill?** The deciding property, shown directly by this test: a rules file only
ever advises. Both times the rule held, it held because the Rule-conflict menu offered "Proceed
anyway" and a human chose not to take it — nothing in `.claude/rules/rest-api-request-dtos.md`
itself can force that choice, and Session 3 even surfaced a third option that would edit the rule
away entirely. If this convention needed to hold with no judgment call available at all — for
example if an earlier real incident had shown someone repeatedly overriding it — a `PreToolUse`
hook blocking `Write` on a new `*Param.java` file under `io/spring/api/` would be the correct
mechanism instead, per the same logic already used for `guard-git.sh` (4.4) and the
`application.properties` refusal hook (1.5): a hook enforces the one property "this action must
never happen," independent of whatever Claude — or a person prompting it — decides in the
moment. A skill wouldn't fit this convention at all, since nothing here is a multi-step procedure
Claude needs to be invoked to run; the convention only concerns the shape of code Claude reads and
writes incidentally while doing other work, which is exactly what a path-scoped rule is for.

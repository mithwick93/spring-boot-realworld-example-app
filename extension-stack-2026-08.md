# 1.6 — Build an extension stack

Repo: `spring-boot-realworld-example-app`, branch `master`.

## What I built

- `.claude/agents/reviewer.md` — read-only reviewer (Read/Grep/Glob only, model haiku,
  6-tool-call turn budget). Checks a diff against this repo's `CLAUDE.md` conventions and
  never edits anything.
- `.claude/skills/review-changes/SKILL.md` — injects the staged diff (`git diff --cached`)
  and hands off to the `reviewer` agent instead of reviewing inline.
- A `SessionStart` hook in `.claude/settings.local.json` — reminds me to run
  `/review-changes` before committing.
- `.claude/output-styles/blunt-reviewer.md` — terse, verdict-first register for review work.

Demo diff to run it against: added `@DuplicatedArticleConstraint` to
`UpdateArticleParam.title`, closing the `PUT /articles/{slug}` duplicate-slug gap I found
back in 1.5.

## Run 1 — nothing staged, Plan Mode on

```
> /review-changes

Nothing is staged, so there's nothing to review yet — run git add on the files you want
checked, then re-run /review-changes.

That said, plan mode is currently active, so I can't run the reviewer subagent or make
any changes right now regardless. Let me know what you'd like to plan.

* Cooked for 4s · done 2:53 PM

> /review-changes

Nothing is currently staged — the diff and file list are both empty. Run git add on the
files you want reviewed, then re-run /review-changes.

* Cooked for 3s · done 2:53 PM

> /output-style

Unknown command: /output-style
```

Two things to fix before the real run: Plan Mode blocks the reviewer delegation outright,
and `/output-style` as a typed command isn't available — turned out to be an Enterprise-plan
restriction (still reachable through `/config`), not a problem with the style file itself.

Staged the diff properly and turned off Plan Mode before trying again.

## Run 2 — diff staged, Plan Mode off, output style set via /config

```
> /review-changes

● reviewer(Review staged UpdateArticleParam diff)
  └ Backgrounded agent (↓ to manage · ctrl+o to expand)

● I've kicked off the reviewer subagent on the staged UpdateArticleParam.java diff. I'll
  relay its findings and verdict verbatim once it finishes.

* Waiting for 1 background agent to finish

● Agent "Review staged UpdateArticleParam diff" finished · 27s

● Findings

VIOLATION: Missing import — @DuplicatedArticleConstraint is referenced on line 13 of
UpdateArticleParam.java but is not imported. The file imports only JsonRootName,
AllArgsConstructor, Getter, and NoArgsConstructor. This will cause a compilation error.
(file:src/main/java/io/spring/application/article/UpdateArticleParam.java:13)

VIOLATION: Inappropriate validator for update context — The DuplicatedArticleValidator
(defined at src/main/java/io/spring/application/article/DuplicatedArticleValidator.java:16)
checks !articleQueryService.findBySlug(Article.toSlug(value), null).isPresent() — it
rejects if any article with that slug exists. On an UPDATE operation, this will incorrectly
fail when updating an article to keep its own title, or when changing to a different title
that collides with itself. This is the TOCTOU pitfall mentioned in CLAUDE.md: a read-check
followed by a non-atomic insert/update, with no DB unique constraint backing it. For
updates, the validator must exclude the article being updated from the collision check —
but the current implementation has no knowledge of which article is being updated.

VIOLATION: Inconsistent with validation pattern in NewArticleParam — NewArticleParam (line
17-18) uses both @NotBlank(message = "can't be empty") and @DuplicatedArticleConstraint on
the title field. UpdateArticleParam has only the custom constraint and no @NotBlank, leaving
empty-string titles unvalidated despite a default value of "" (line 14). Per CLAUDE.md,
javax.validation annotations belong directly on DTO fields; this asymmetry breaks that
pattern.

---

VERDICT: needs changes

The missing import alone blocks compilation. Beyond that, applying a create-time uniqueness
constraint to an update DTO is semantically unsafe without excluding the article being
updated, per the repo's documented TOCTOU pattern pitfall. The validator needs knowledge of
article context (the ID being updated) that it cannot currently access, or the constraint
must be removed from UpdateArticleParam entirely and handled in the controller logic
instead.

* Brewed for 45s · done 2:58 PM
```

`/config` confirmed `Output style: Blunt Reviewer` was active. The register shift versus
Run 1 is visible in the transcript above — Run 1 is conversational prose, Run 2 is tagged
`VIOLATION:`/`VERDICT:` lines with no preamble, matching what `blunt-reviewer.md` asks for.

## Checking the reviewer's findings against the actual files

The delegation itself worked cleanly — a real background agent, not an inline review — but
I didn't take the three findings at face value. Checked each one:

1. **"Missing import" — false.** `UpdateArticleParam.java` and `DuplicatedArticleConstraint.java`
   are both `package io.spring.application.article;` (same package in both files, line 1).
   No import needed in Java for a same-package reference — this wouldn't fail to compile.
2. **"Validator can't exclude the article's own slug" — real.** Confirmed in
   `DuplicatedArticleValidator.java:16` — the check has no way to know which article is
   being updated, so resubmitting an unchanged title gets falsely flagged as a duplicate.
   This is the same gap I'd already noted when I wrote this diff.
3. **"Missing @NotBlank, inconsistent with NewArticleParam" — misleading.** Checked
   `Article.update()`: all three `UpdateArticleParam` fields are designed so blank means
   "leave this field unchanged" (`Util.isEmpty` guards on title/description/body alike).
   `NewArticleParam` needs `@NotBlank` because creation has no "leave unchanged" option —
   the two DTOs serve different purposes, so the asymmetry isn't actually a bug.

Only one of the three findings holds up. The stack's mechanics (delegation, structured
output, verdict) worked exactly as intended; the content still needs a human check before
acting on it.

## Note

Not fixing the validator issue (finding 2) as part of this exercise — the diff exists to
exercise the review stack, not to ship a finished fix. Leaving it staged and documenting the
limitation here rather than merging it quietly.

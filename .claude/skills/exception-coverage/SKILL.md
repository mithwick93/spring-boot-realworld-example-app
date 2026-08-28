---
name: exception-coverage
description: Check whether an exception type is actually handled by both the REST and GraphQL exception handlers in this codebase. Use before assuming an exception path is safe, or when adding a new exception type anywhere in the write path.
argument-hint: <exception-class-name>
---

This codebase has two parallel API adapters — REST (`io.spring.api`) and GraphQL
(`io.spring.graphql`) — each with its own exception handler:
- `src/main/java/io/spring/api/exception/CustomizeExceptionHandler.java` (REST)
- `src/main/java/io/spring/graphql/exception/GraphQLCustomizeExceptionHandler.java` (GraphQL)

A fix applied to one is easy to forget on the other. Check coverage for `$ARGUMENTS`:

1. Search the codebase for every place `$ARGUMENTS` can actually be thrown — thrown
   explicitly (`throw new $ARGUMENTS(...)`), or thrown implicitly by a framework/library
   call (e.g. a DB unique constraint violation throwing `DataIntegrityViolationException`,
   a `@Valid` annotation throwing `ConstraintViolationException`).
2. Open `CustomizeExceptionHandler.java` and check whether `$ARGUMENTS` (or one of its
   superclasses) has an `@ExceptionHandler` entry. Quote the exact line if it exists.
3. Open `GraphQLCustomizeExceptionHandler.java` and check the same thing for the GraphQL
   path.
4. Report one of three outcomes, with file:line evidence for each claim — do not guess:
   - **Covered on both paths** — quote both handler methods.
   - **Covered on one path only** — name which adapter is missing it, and what a caller
     hitting that adapter actually sees today (e.g. "unmapped 500" vs the shaped error the
     other adapter returns).
   - **Covered on neither** — flag as a gap and name the entry point(s) where it can
     surface.
5. If the exception can be thrown from more than one entry point (e.g. both create and
   update paths), check coverage at each entry point separately — coverage of the
   exception type is not the same as coverage of every path that can throw it.

Do not fix the gap as part of running this skill — report findings only. Fixing is a
separate, deliberate step.

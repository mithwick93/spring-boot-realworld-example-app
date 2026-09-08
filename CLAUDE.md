# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

- Build: `./gradlew build`
- Run all tests: `./gradlew test`
- Run a single test class: `./gradlew test --tests "io.spring.api.UsersApiTest"`
- Run a single test method: `./gradlew test --tests "io.spring.api.UsersApiTest.someMethodName"`
- Run the app locally: `./gradlew bootRun` (serves at `http://localhost:8080`, e.g. `GET /tags` — note the API root is `/`, not `/api`)
- Format code (required before commit, uses Google Java Format via Spotless): `./gradlew spotlessJavaApply`
- Build a Docker image (via Cloud Native Buildpacks, no Dockerfile in repo): `./gradlew bootBuildImage --imageName spring-boot-realworld-example-app`

Java 11, Spring Boot 2.6.3, Gradle (no Maven). Note: the `clean` task also deletes the local `dev.db` SQLite file.

## Architecture

This is a Spring Boot implementation of the RealWorld API spec, structured as light DDD + CQRS with two parallel adapters (REST and GraphQL) over a shared domain/application core. Base package: `io.spring`.

- `api/` — Spring MVC REST controllers (`ArticleApi`, `UsersApi`, `TagsApi`, etc.), plus `api/security` (`WebSecurityConfig`, `JwtTokenFilter`) and `api/exception`.
- `graphql/` — GraphQL adapter (via Netflix `dgs-framework`) exposing the same underlying domain/application layers as an alternative to REST.
- `core/` — Domain layer: entities, domain services, and repository *interfaces*, organized by aggregate (`core/article`, `core/comment`, `core/favorite`, `core/user`, `core/service`).
- `application/` — CQRS read-model/query layer: DTOs and query services consumed by both `api` and `graphql`, organized by domain (`application/article`, `application/user`, `application/data`).
- `infrastructure/` — Technical implementations of `core` interfaces: MyBatis mapper interfaces and read services (`infrastructure/mybatis`), MyBatis-backed repository implementations (`infrastructure/repository`), and other service implementations e.g. `DefaultJwtService` (`infrastructure/service`).

When adding a feature, the typical flow is: define/extend a `core` entity + repository interface → implement persistence in `infrastructure` (MyBatis mapper XML under `src/main/resources/mapper/*.xml` + repository impl) → expose via `api` (and optionally `graphql`) → add read-side DTOs/queries in `application` if the feature needs list/detail views distinct from the write model.

### Persistence
- MyBatis (not JPA/Hibernate) — SQL lives in `src/main/resources/mapper/*.xml`, mapped to interfaces in `infrastructure/mybatis/mapper`.
- Database: SQLite. Dev DB is a file (`dev.db`, configured in `src/main/resources/application.properties`); tests use an in-memory SQLite DB (`src/main/resources/application-test.properties`).
- Schema migrations: Flyway, single migration at `src/main/resources/db/migration/V1__create_tables.sql`.

### Security
- Spring Security with a custom `JwtTokenFilter` (`api/security/JwtTokenFilter.java`) inserted before `UsernamePasswordAuthenticationFilter`, extracting `Authorization: <scheme> <token>` and loading the user via `UserRepository`.
- JWT creation/parsing: `core/service/JwtService` interface, implemented by `infrastructure/service/DefaultJwtService` (jjwt, HS512, secret + expiry from `jwt.secret`/`jwt.sessionTime` in `application.properties`).
- Passwords are hashed via Spring Security's `PasswordEncoder` (`BCryptPasswordEncoder` bean in `WebSecurityConfig`) — no custom encryption service.
- Route access rules (public vs. authenticated) are defined in `api/security/WebSecurityConfig.java`.

## Test Plan

Every new test — human- or Claude-written — should satisfy this before it's considered done.

- **Scope & risk:** full unit coverage for new pure functions/validators (`io.spring.Util`, `io.spring.core.*`, `io.spring.application.*`). MyBatis mapper XML and Flyway migrations aren't unit-tested directly — they're exercised through repository/API integration tests instead. Depth scales with risk: highest-risk areas are auth (`JwtTokenFilter`, `DefaultJwtService`), the duplicate-constraint validators (`Duplicated*Validator` — this repo has a documented history of TOCTOU races here, see Conventions below), and exception-handler field-name mapping (`CustomizeExceptionHandler.getParam`, `GraphQLCustomizeExceptionHandler.getParam`).
- **Types & levels, with exact runner commands:**
  - Unit (pure functions/validators, no Spring context): `./gradlew test --tests "io.spring.<package>.<ClassName>Test"`
  - Integration (API controllers/repositories, full Spring context, in-memory SQLite): `./gradlew test --tests "io.spring.api.<Name>ApiTest"`
  - Full suite (run before any commit touching shared code): `./gradlew test`
- **Case coverage** — every test class should include, where applicable: positive (typical valid input), negative (invalid/missing/malformed input), boundary (null, empty string, single-character, exact-limit values, one past a limit).
- **Rules & exit:**
  - A test is written and confirmed *failing* before its implementation exists — a test that passes pre-implementation proves nothing.
  - A change is done when its own test class is green **and** the full suite (`./gradlew test`) is green with no regressions elsewhere.
  - Test method names follow the existing convention: `should_<outcome>_<condition>` (see Conventions below).

## Conventions (undocumented, found by reading source — `/init` missed these)

- **Test method naming:** `should_<outcome>_<condition>` — all lowercase, underscore-separated (e.g. `should_create_user_success`, `should_show_error_message_for_blank_username`). Every test in `src/test/java/io/spring/api` follows this; some auth-flow tests also extend a shared `TestWithCurrentUser` base class rather than duplicating setup.
- **Request DTOs:** package-private classes declared at the bottom of the controller file they belong to, not separate files (see `LoginParam` in `UsersApi.java`). Annotate with `@JsonRootName` to match the RealWorld response envelope (e.g. `{"user": {...}}`, `{"profile": {...}}`).
- **Validation:** `javax.validation` annotations directly on DTO fields (`@NotBlank(message = "...")`, `@Email(message = "...")`), triggered via `@Valid @RequestBody`. Messages are hand-written strings, no i18n bundle.
- **Pitfall — error responses are NOT uniformly shaped.** Field-validation errors return `{"errors": {"field": ["msg"]}}` (via `CustomizeExceptionHandler` → `ErrorResource`/`FieldErrorResource`). Auth-type exceptions (e.g. `InvalidAuthenticationException`) instead return a flat `{"message": "..."}`. Check `CustomizeExceptionHandler` before assuming one shape for a new endpoint.
- **Pitfall — cross-parameter Bean Validation constraints must not use the default `<cross-parameter>` node.** A method-level constraint validator (`ConstraintValidator<..., Object[]>` with `@SupportedValidationTarget(ValidationTarget.PARAMETERS)`) that fails without redirecting the violation produces a `propertyPath` like `updateArticle.<cross-parameter>` (2 segments). Both `CustomizeExceptionHandler.getParam` (REST) and `GraphQLCustomizeExceptionHandler.getParam` (GraphQL) special-case/derive the field name differently for 2-segment paths — REST surfaces the literal string `"<cross-parameter>"`, GraphQL surfaces `""` (colliding with other empty-field errors). Fix: in the validator, call `context.disableDefaultConstraintViolation()` then `.buildConstraintViolationWithTemplate(...).addParameterNode(<index>).addPropertyNode("<field>").addConstraintViolation()` to produce a 3-segment path (e.g. `updateArticle.<param>.title`), which both handlers already parse correctly — same convention as cascaded `@Valid` field violations. Also: once a `@Constraint` has both a generic validator and a cross-parameter validator registered, its annotation must declare `ConstraintTarget validationAppliesTo() default ConstraintTarget.IMPLICIT;`, or method-level usage throws `ConstraintDefinitionException: mixes generic and cross-parameter validation`. See `DuplicatedArticleConstraint`/`DuplicatedArticleUpdateValidator` for a worked example.
- **Simple path-variable actions** (no request body) skip the DTO step entirely — see `ProfileApi.follow`/`unfollow`, which take only `@PathVariable` + `@AuthenticationPrincipal`.

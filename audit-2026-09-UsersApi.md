# AI Code Review Audit — UsersApi.java

## Session Information

| Field | Value |
|---|---|
| Date | 2026-09-09 |
| Reviewer | Mithila Wickramarathne |
| AI Tool | Claude Code (Sonnet 5) |
| Repository | spring-boot-realworld-example-app |
| Branch | refactor/cursor-page-parameter-limit-validation |
| Review type | Point-in-time security review of existing code (not a pre-merge gate) |

## Scope

**File reviewed:** `src/main/java/io/spring/api/UsersApi.java` (registration + login endpoints), with supporting context from `application.properties`, `WebSecurityConfig.java`, `JwtTokenFilter.java`, `DefaultJwtService.java`, `RegisterParam.java`, and the `Duplicated*Validator`/`Constraint` classes.

**Review focus:** OWASP-style categories — injection, authentication, sensitive data, access control, misconfiguration, XSS, dependencies, logging.

**Method:** the review prompt was run twice against the same file — once before a CLAUDE.md Security Requirements section existed, once after — to test whether the rules actually change output.

## AI Findings

### Finding 1: Hardcoded JWT signing secret
| Attribute | Value |
|---|---|
| Severity | CRITICAL (AI reported CRITICAL on run 1, HIGH on run 2 — see Human Overrides) |
| Location | `application.properties:9` |
| Category | Security — Sensitive data / Authentication |

Signing secret committed in plaintext; anyone with repo/history access can forge a valid token for any user (`DefaultJwtService.toToken`, `JwtService`/`JwtTokenFilter` trust chain).
**Human Assessment:** Confirmed valid finding.
**Action Taken:** Documented; not fixed — out of scope for this homework's deliverable.

### Finding 2: User enumeration via validation messages
| Attribute | Value |
|---|---|
| Severity | HIGH |
| Location | `DuplicatedEmailConstraint.java:11`, `DuplicatedUsernameConstraint.java:11` |
| Category | Security — Access control |

Default constraint messages (`"duplicated email"`, `"duplicated username"`) surfaced verbatim through `CustomizeExceptionHandler`, telling an unauthenticated caller exactly which identifier is already registered.
**Human Assessment:** Confirmed valid finding (verified against the literal annotation defaults).
**Action Taken:** Documented; not fixed.

### Finding 3: No rate limiting on `/users`, `/users/login`
| Attribute | Value |
|---|---|
| Severity | HIGH (AI reported HIGH on run 1, MEDIUM on run 2) |
| Location | `WebSecurityConfig.java:57-58` |
| Category | Security — Authentication |

Both routes are `permitAll()`; no throttling library exists anywhere in the repo (confirmed by full-repo search).
**Human Assessment:** Confirmed valid finding.
**Action Taken:** Documented; not fixed.

### Finding 4: Login timing side-channel
| Attribute | Value |
|---|---|
| Severity | MEDIUM |
| Location | `UsersApi.java:49-51` |
| Category | Security — Authentication |

`passwordEncoder.matches(...)` only runs when the email exists, creating a measurable timing difference between existing and non-existing accounts.
**Human Assessment:** Confirmed as a real pattern; practical exploitability over a network is low without controlled measurement conditions.
**Action Taken:** Documented; not fixed.

### Finding 5: Missing length bounds on registration/login fields
| Attribute | Value |
|---|---|
| Severity | MEDIUM |
| Location | `RegisterParam.java`, `UsersApi.java` (`LoginParam`) |
| Category | Security — Input validation |

Only `@NotBlank`/`@Email` present; no `@Size` on email/username/password, on an unauthenticated endpoint.
**Human Assessment:** Confirmed valid finding.
**Action Taken:** Documented; not fixed.

### Finding 6: JWTs not revocable
| Attribute | Value |
|---|---|
| Severity | LOW |
| Location | `DefaultJwtService.java:32-38`, `application.properties:10` |
| Category | Security — Authentication |

24-hour token lifetime with no token-version or blacklist check; a stolen token stays valid after a password change.
**Human Assessment:** Confirmed; correctly scoped by the AI as a separate follow-up rather than a one-line fix.
**Action Taken:** Documented; not fixed.

### Finding 7: Authorization header scheme not validated
| Attribute | Value |
|---|---|
| Severity | LOW |
| Location | `JwtTokenFilter.java:50-61` |
| Category | Security — Access control |

`getTokenString` only checks that the header splits into 2+ parts; the scheme word (`Token`, `Bearer`, or anything else) is never validated.
**Human Assessment:** Confirmed valid finding.
**Action Taken:** Documented; not fixed.

### Finding 8: MyBatis DEBUG logging on mapper package
| Attribute | Value |
|---|---|
| Severity | LOW |
| Location | `application.properties:19-20` |
| Category | Security — Logging |

Package-wide DEBUG logging could log bound parameters (emails, hashed passwords) if user-related mappers fall under the logged package.
**Human Assessment:** Confirmed the config setting; whether bound params actually appear in these log lines at runtime was not independently verified.
**Action Taken:** Documented; not fixed.

### Finding 9: Internal class name leaked in error responses
| Attribute | Value |
|---|---|
| Severity | LOW |
| Location | `CustomizeExceptionHandler.java:122` |
| Category | Security — Sensitive data |

`violation.getRootBeanClass().getName()` returns a fully-qualified internal class name to the client.
**Human Assessment:** Confirmed; minor severity (DTO class name, not a stack trace or file path).
**Action Taken:** Documented; not fixed.

### Finding 10: CORS wildcard origin
| Attribute | Value |
|---|---|
| Severity | LOW |
| Location | `WebSecurityConfig.java:69-75` |
| Category | Security — Access control |

`setAllowedOrigins("*")` on a bearer-token API; `setAllowCredentials(false)` limits blast radius but any third-party page can still relay login/register calls through a victim's browser. Surfaced only in the post-CLAUDE.md-rules run, not the pre-rules run.
**Human Assessment:** Confirmed valid finding.
**Action Taken:** Documented; not fixed.

### Finding 11: Dependency CVE claim — needs investigation
| Attribute | Value |
|---|---|
| Severity | Unrated — needs investigation |
| Location | `build.gradle:42-44` (`io.jsonwebtoken:jjwt-*:0.11.2`) |
| Category | Security — Dependencies |

The post-rules review asserted "jjwt 0.11.2 is current/safe" without citation. `jjwt-jackson:0.11.2`'s transitive Jackson-databind version has documented 2020 CVEs (CVE-2020-14060/14061/14062/14195/24616/24750). Spring Boot 2.6.3's own dependency-management BOM may override the resolved Jackson version to a patched one — unconfirmed without a real `./gradlew dependencies` check.
**Human Assessment:** Needs further investigation — see Human Overrides.
**Action Taken:** Not fixed; flagged for a real dependency-tree check before closing.

## Human Overrides

| AI Recommendation | Human Decision | Rationale |
|---|---|---|
| Run 2 rated Finding 1 (hardcoded secret) as HIGH | Recorded as CRITICAL | A leaked signing secret allowing token forgery for any user is a full authentication-bypass condition, which standard severity taxonomies treat as CRITICAL, not HIGH. |
| Run 2 stated "jjwt 0.11.2 is current/safe" | Recorded as needs-investigation, not accepted | The claim had no citation; a targeted check found real (if indirect, via a transitive dependency) CVE history for that exact version, and actual exploitability depends on Spring Boot's dependency-resolution override, which wasn't checked. |

## Summary

| Metric | Count |
|---|---|
| Total findings | 11 |
| Confirmed issues | 10 |
| False positives | 0 |
| Needs investigation | 1 |
| Issues fixed | 0 |
| Issues deferred | 11 |

Separately, the `detect-secrets` baseline scan (not the code review) flagged one false positive: an already-expired test JWT fixture at `DefaultJwtServiceTest.java:39`, used to test expired-token rejection — not a live credential.

## Evaluate

- **Real vs. false positive:** 10 of 11 findings confirmed real; 0 false positives among the AI's own findings. One additional needs-investigation item (dependency CVEs) was surfaced independently, not by either review run.
- **What the AI missed:** neither run checked the mapper XML for SQL injection risk directly (confirmed safe on independent check: `#{}` parameter binding throughout, no string concatenation) or verified the dependency-CVE claim it made in run 2.
- **Did the CLAUDE.md Security Requirements section change output:** yes — two findings (secret handling, `@Size` bounds) explicitly cited the new rules by name in run 2; five run-1 findings dropped from run 2's output; one new finding (CORS) appeared only in run 2.
- **Would this entry make sense to someone who wasn't in the room:** each finding carries file:line, severity, and an explicit human assessment; the two overrides above document exactly where AI judgment was corrected and why.

## Sign-Off

- [x] All findings documented with severity and human assessment
- [x] Review documented completely, including both review runs and the rule-change comparison
- [ ] Not applicable: no code changes were made as part of this homework (review-only exercise)

**Reviewer:** Mithila Wickramarathne
**Date:** 2026-09-09

# 0001. Request correlation ID via MDC-backed servlet filter

Date: 2026-09-29

## Status

Accepted

## Context

Log lines produced while handling a single HTTP request had no shared identifier, making it hard
to grep/correlate all log output belonging to one request across `api/`, `core/`, and
`infrastructure/` layers — especially once requests fan out to multiple log statements.

## Decision

Add `RequestIdFilter` (`api/security/RequestIdFilter`), a `OncePerRequestFilter` registered as a
bean in `WebSecurityConfig` and installed via
`http.addFilterBefore(requestIdFilter(), JwtTokenFilter.class)` — i.e. before authentication, so
a correlation ID is present in logs even for requests that are rejected by `JwtTokenFilter`.

The filter:
- Reuses an inbound `X-Request-Id` request header if the caller (e.g. an upstream gateway) already
  set one, otherwise generates a new `UUID`.
- Echoes the ID back as an `X-Request-Id` response header, so a client/gateway can tie a specific
  response to server-side log output.
- Puts the ID into SLF4J's `MDC` under key `requestId` for the lifetime of the request, and removes
  it in a `finally` block to avoid leaking values across pooled request-handling threads.

No new dependency was introduced — `MDC` is part of the `slf4j-api` already used throughout the
project.

## Consequences

- Any log pattern that includes `%X{requestId}` will now surface the correlation ID automatically;
  no changes are needed at individual call sites.
- Because the filter trusts a caller-supplied `X-Request-Id`, it assumes requests arrive from a
  trusted network boundary (e.g. an internal gateway) that either sets this header correctly or
  omits it — a directly internet-facing deployment without such a gateway would let a client set an
  arbitrary value into logs under this key.
- The filter runs before `JwtTokenFilter`, so future filters that also need to run pre-auth and
  want access to the correlation ID must be ordered after `RequestIdFilter`.
- Future filters/interceptors that want to correlate their own log output with a request should key
  off `RequestIdFilter.MDC_KEY` (`"requestId"`) rather than inventing a new MDC key.

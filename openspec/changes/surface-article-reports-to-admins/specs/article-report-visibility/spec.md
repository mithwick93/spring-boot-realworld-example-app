## Purpose

Lets a user see the reports they have personally filed, closing the "reports vanish into a black hole" gap flagged during `article-report`'s peer review — without introducing any admin or authorization concept this codebase does not have.

## ADDED Requirements

### Requirement: A reporter can list their own filed reports
The system SHALL allow an authenticated user to retrieve the list of reports they have personally filed, across all articles, using only their existing JWT identity — no new permission or role is introduced.

#### Scenario: User has filed reports on multiple articles
- **WHEN** an authenticated user who has reported two different articles requests their own report list
- **THEN** the response includes exactly the two reports they filed, each identified by article slug, reason, and creation timestamp

#### Scenario: User has never filed a report
- **WHEN** an authenticated user who has never reported any article requests their own report list
- **THEN** the response is a successful, empty list — not an error

#### Scenario: Unauthenticated request
- **WHEN** a request is made with no valid JWT
- **THEN** the response is `401 Unauthorized`, matching the existing unauthenticated behavior for `POST /articles/{slug}/report`

### Requirement: A user cannot see another user's reports through this endpoint
The system SHALL scope every result strictly to the requesting user's own identity; there is no query parameter, path segment, or other mechanism to request another user's reports through this capability.

#### Scenario: Request carries a valid token for user A
- **WHEN** user A calls the report-listing endpoint
- **THEN** only reports where `user_id` equals user A's id are ever returned, regardless of how many other users have filed reports on the same or different articles

## Out of Scope (for this capability)

- Admin- or moderator-facing visibility into any user's reports — blocked on this codebase having no authorization/role layer at all (see the proposal's scope-down note); a separate, future capability once that infrastructure exists.
- Any change to the existing `article-report` capability's data model or response shapes — this capability only reads existing data.
- Filtering, pagination, or sorting of the reporter's own list — the expected volume (reports filed by one user) does not justify it yet; can be added later without a breaking change if it does.

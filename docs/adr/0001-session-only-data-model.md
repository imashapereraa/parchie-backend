# ADR-0001: Session-only data model

## Status

Accepted

## Context

Parchie needs to store collaborative document state. Traditional approaches would include user accounts, document ownership, permissions, and access control lists.

The product goal is radical simplicity: open a link, write together, export something nice. No signup, no accounts, no permission management.

## Decision

The backend has exactly one entity: **Session**. There are no users, no auth tables, no ownership records.

- Sessions are identified by UUID.
- The encryption key is carried in the URL fragment and never reaches the server.
- Anyone with the link can join.
- Sessions expire automatically after a configurable TTL (default 7 days from last activity).

## Consequences

- **Simple.** One table, one repository, one set of CRUD endpoints.
- **No access control beyond the link.** If you have the URL, you're in. Password protection (optional) is the only gate.
- **No audit trail.** The server can't attribute edits to individuals since there are no user identities.
- **Future accounts (v1.4) will be additive.** They'll extend TTL and provide a session list, not change the core data model. Anonymous joining remains the default.

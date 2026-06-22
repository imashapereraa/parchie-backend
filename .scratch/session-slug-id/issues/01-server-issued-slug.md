# 01 — Server-issued slug so shared URLs resolve in any browser

Status: ready-for-human
Labels: bug

> Backend complete; frontend still needs to switch from client-generated slugs to consuming `SessionMetadataDto.slug` and passing the slug (not the localStorage UUID) at all entry points.

## What

A session shared by URL (`parchie.app/s/<slug>`) does not load in a browser that did not create the session. The creator sees their content; the recipient sees an empty editor. Both editors stay stuck on "Connecting…".

## Root cause

The slug in the URL only exists in the creator's `localStorage` (`parchie:{slug}:bid -> backendUUID`). The server has never heard of it. Concretely:

- The REST snapshot endpoints (`SessionController.java:54-74`) only accept a `UUID` path variable, so the recipient's browser cannot call `GET /api/sessions/<slug>/state` to bootstrap.
- After commit `9e38bb1`, `SessionRelayHandler` accepts any room name, but it buckets peers by the raw string. The creator's browser is connecting with the backend UUID (per `.scratch/websocket-frontend/issues/02-ws-uuid-mismatch.md`) while the recipient's browser connects with the slug. They end up in two different `peersByRoom` entries and never exchange frames.

So two browsers visiting the same URL silently land in two different rooms with no shared snapshot.

## Fix

Make the slug a real server-side identifier with a 1:1 mapping to the session UUID. All entry points (REST + WS) accept either the slug or the UUID and resolve to the same row.

### Backend changes

1. **Migration `V3__add_session_slug.sql`** — add nullable, unique `slug VARCHAR(32)` to `sessions` with a supporting index.
2. **`Session.java`** — add `slug` field + getter/setter.
3. **`SessionRepository.java`** — `Optional<Session> findBySlug(String slug)`.
4. **`SessionService.java`**
   - `createSession()` generates a slug (three short groups, e.g. `r7h-rxkt-fix`). Retry on unique-violation up to a small bound.
   - `resolveId(String idOrSlug) -> UUID` accepting either form. UUIDs parse; non-UUIDs go through `findBySlug`. Throws `SessionNotFoundException` on miss.
   - All existing `UUID`-keyed methods stay; callers route through `resolveId` first.
5. **`SessionController.java`** — path vars become `String idOrSlug`, call `resolveId`.
6. **`SessionRelayHandler.java`** — `parseRoom` returns the resolved UUID string; peers bucket by UUID regardless of which form the client connected with.
7. **`SessionMetadataDto`** — expose `slug` so the frontend can stop generating its own.

### Migration / data note

Existing rows have `slug IS NULL`. We accept that — there is no production data yet. Old links that relied on a localStorage mapping will not resolve, which is the same as today.

## Acceptance

- Create a session in Browser A. URL is `/s/<slug>#<key>`. Backend log shows the slug in the create response.
- Open the same URL in a fresh private window (Browser B).
  - `GET /api/sessions/<slug>/state` returns the snapshot (200 with bytes, or 204 if none yet).
  - `WS /ws/sessions/<slug>` lands in the same `peersByRoom` bucket as Browser A's connection (whether A used slug or UUID).
- Typing in A propagates to B in real time. Closing A then refreshing B still shows the last snapshot.
- No new POLICY_VIOLATION closes; the recent `9e38bb1` leniency stays in place but is now backed by real resolution.

## Files

- `src/main/resources/db/migration/V3__add_session_slug.sql` (new)
- `src/main/java/com/parchie/model/Session.java`
- `src/main/java/com/parchie/repository/SessionRepository.java`
- `src/main/java/com/parchie/service/SessionService.java`
- `src/main/java/com/parchie/controller/SessionController.java`
- `src/main/java/com/parchie/websocket/SessionRelayHandler.java`
- `src/main/java/com/parchie/dto/SessionMetadataDto.java`
- Tests under `src/test/java/com/parchie/...` for slug create + resolve at REST and WS.

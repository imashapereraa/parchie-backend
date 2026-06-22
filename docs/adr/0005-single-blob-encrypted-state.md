# ADR-0005: Single-blob encrypted state, object storage deferred

## Status

Accepted

## Context

A scalability review of the V1 schema raised whether `encrypted_state` should stay as a `BYTEA` column on Postgres or be stored differently — append-only updates table, or moved to S3-compatible object storage entirely.

We surveyed how production Yjs / collaboration backends handle this:

- **Hocuspocus** (the dominant self-hosted Yjs server): single overwritable blob via `fetch`/`store` callbacks. Recommended schema is two columns: `(name PK, data BLOB)`.
- **y-leveldb**: append-only updates with optional manual `flushDocument()` merge.
- **yhub** (yjs project's scale-out reference): hybrid — Redis stream for live updates, periodic worker merges to a single blob in S3, metadata + S3 reference in Postgres.
- **PartyKit / y-partykit**: default = snapshot mode (single blob), history mode optional and capped at 10MB.
- **Skiff, Notesnook** (E2E-encrypted collaborative editors): server is a dumb opaque-blob store; client encrypts before sending. No server-side merge intelligence — the server cannot read the bytes.

Two patterns emerge:

1. For **E2E-encrypted relays**, single blob is structural, not a choice. The server cannot merge, dedupe, or interpret updates because it cannot read them — so append-only buys nothing the single-blob model does not.
2. The scale-out answer is not "append-only" but "move the blob to object storage" (yhub's pattern). Postgres holds metadata + a URI; the blob lives in S3-compatible storage.

The MVP scale target is **200 concurrent users**. Back-of-envelope: ~50 active Sessions, snapshots debounced every ~10s during sustained editing, ~5 state PUTs/sec, ~1.5 MB/s of Postgres write throughput including WAL amplification. The WebSocket relay does not touch the DB per message. This is 50–100× under any plausible single-blob Postgres ceiling on a sensible VPS.

## Decision

`encrypted_state` is stored as a `BYTEA` column on the `sessions` table. Postgres TOAST handles the cold/hot split automatically — large BYTEA values live in the TOAST table and are not read by metadata-only queries.

There is **no `version` column** for optimistic concurrency. The relay invariant (ADR-0003) is that the server is a dumb pipe; the CRDT is the source of truth, and the blob is a recovery cache. Last-writer-wins on the blob is acceptable because clients re-snapshot from their live `Y.Doc` and converge.

Object storage is the documented upgrade path, **not** the current design. Triggering this migration requires evidence of sustained Postgres write pressure or storage cost — not "we have 200 users."

## Consequences

- **Simpler.** One table, one column, no second service, one backup story, one failure mode per request.
- **Matches the industry default** for E2E-encrypted Yjs relays (Hocuspocus's model).
- **Migration to object storage is small and self-healing** when the time comes: add an `encrypted_state_key VARCHAR` column, run a background job that writes BYTEA to S3 and populates the key, then drop the BYTEA column. The CRDT means clients tolerate any blob-state staleness during cutover by re-snapshotting on next edit.
- **No premature seam.** We do not introduce a "blob storage adapter" interface today. When the migration happens, the adapter has two real implementations (Postgres-blob legacy + S3 new) at exactly one point in time, and only the new one survives.
- **The "single-VPS" simplicity of ADR-0003 is preserved.** No external object store, no Redis, no second backup target.
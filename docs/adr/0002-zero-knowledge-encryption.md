# ADR-0002: Zero-knowledge encryption

## Status

Accepted

## Context

Users will write potentially sensitive content in collaborative sessions. The product promises that the server cannot read what users write.

## Decision

All document content is encrypted client-side using AES-256-GCM via the Web Crypto API before being sent to the server. The encryption key is placed in the URL fragment (`parchie.app/s/abc123#key`), which browsers never send to the server.

The backend is a **relay**: it stores and forwards opaque encrypted blobs. It never decrypts, parses, or validates document content.

## Consequences

- **Privacy by design.** Server compromise does not expose document content.
- **No server-side search, indexing, or content validation.** The server literally cannot read what it stores.
- **Key management is the client's problem.** If the URL fragment is lost, the content is unrecoverable.
- **The backend treats `encrypted_state` as an opaque `blob`.** No schema, no migrations on content structure — that's all client-side.

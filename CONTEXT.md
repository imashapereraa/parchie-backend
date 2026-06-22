# Parchie — Domain Context

Parchie is a real-time collaborative editor. Users share a link, write together without accounts, pick a CSS theme, and export styled HTML. The mascot is a champagne loaf cat sitting on a note.

## Glossary

| Term | Definition | Avoid |
|------|-----------|-------|
| **Session** | A single collaborative document identified by a unique URL. The only first-class entity in the system. | "document", "room", "workspace" |
| **Encrypted state** | The opaque blob the server stores for a session. Content is AES-256-GCM encrypted client-side; the server never sees plaintext. | "document content", "payload" |
| **TTL** | The Session's expiry timestamp (`expires_at`). Default is 7 days from creation; reset on each snapshot write and on explicit "extend TTL" actions. After expiry the session is garbage-collected. | "expiration timer" |
| **Locked session** | A session set to read-only. Participants can view but not edit. | "frozen", "archived" |
| **Theme** | A named CSS stylesheet applied to the editor preview and HTML export. Built-in set: Minimal, Noir, Academic, Terminal, Magazine. | "template", "skin" |
| **Export** | Producing a self-contained artifact from a session — styled HTML, raw Markdown, or PDF via browser print. | "download", "save" |
| **Presence** | Live cursor and selection state broadcast to all participants in a session. | "awareness" |
| **Relay** | The backend's role in real-time sync — it forwards encrypted CRDT updates between clients without interpreting them. | "sync server", "collaboration server" |

## Data model

One entity:

```
Session
├── id: UUID (PK)
├── created_at: timestamp
├── expires_at: timestamp (default now() + 7 days)
├── is_locked: boolean
├── password_hash: varchar (nullable)
└── encrypted_state: blob (nullable — null until first snapshot write)
```

One table, one row per Session. Postgres TOAST keeps the `encrypted_state` blob off-page automatically, so metadata-only queries do not read it. See ADR-0005.

No users table. No auth table. Sessions are the only thing the server knows about.

## Key invariants

- **Zero-knowledge server.** The encryption key lives in the URL fragment (`#key`). The server never receives it. All content stored on the server is encrypted.
- **No account required.** Session creation and joining are always anonymous.
- **Session expiry.** Sessions expire at `expires_at`. Snapshot writes and "extend TTL" actions push `expires_at` forward; nothing else does. A scheduled cleanup job garbage-collects rows where `expires_at < now()`.
- **Relay only.** The backend does not parse, validate, or transform document content. It stores and forwards encrypted blobs.

## Backend responsibilities

1. **REST API** — create sessions, fetch session metadata, update session settings (lock, TTL, password).
2. **WebSocket relay** — accept raw WebSocket connections, broadcast encrypted Yjs binary sync messages and presence to session participants.
3. **TTL cleanup** — scheduled job that deletes sessions past their expiry.
4. **Password gate** — if a session has a `password_hash`, require the correct password before granting access.

## Tech stack (backend)

- Java 21, Spring Boot
- Spring WebSocket (raw, not STOMP) for real-time relay
- Spring Web MVC for REST
- Spring Data JPA for persistence
- Spring Scheduler for TTL cleanup
- `org.mindrot:jbcrypt` for password hashing (no Spring Security until needed)
- PostgreSQL
- Docker Compose for local dev

## Roadmap context

The project is a learning vehicle for Java Spring Boot, real-time systems, CRDT sync, E2E encryption, and full-stack deployment. The product being useful is a bonus.

Current target: **v1.0 MVP** — session CRUD, real-time relay, E2E encryption, themed export.

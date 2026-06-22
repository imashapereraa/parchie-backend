# 01 — Session JPA Entities

Status: done

## What

Create two JPA entities in `com.parchie.model` that map to the V1 migration schema:
- `Session` → `sessions` table
- `SessionState` → `session_states` table

## Session entity

- [ ] Class annotated with `@Entity`, table name `sessions`
- [ ] Fields matching the migration:
  - `id` — UUID, auto-generated, primary key
  - `createdAt` — Instant, set on creation, not updatable
  - `lastActiveAt` — Instant, set on creation, updatable
  - `ttlDays` — int, default 7
  - `isLocked` — boolean, default false
  - `passwordHash` — String, nullable
- [ ] `@OneToOne(mappedBy = "session", cascade = CascadeType.ALL, optional = true)` to `SessionState`
- [ ] `@PrePersist` callback to set `createdAt` and `lastActiveAt` on insert
- [ ] Helper method `isExpired()` that checks if `lastActiveAt + ttlDays` is in the past
- [ ] Getters and setters for all mutable fields

## SessionState entity

- [ ] Class annotated with `@Entity`, table name `session_states`
- [ ] Shared primary key pattern — `session_id` is both the PK and the FK:
  - `@Id` field `sessionId` (UUID)
  - `@OneToOne @MapsId @JoinColumn(name = "session_id")` to `Session`
- [ ] `encryptedState` — `byte[]`, annotated `@Lob @Column(columnDefinition = "bytea")`
- [ ] `updatedAt` — Instant, set on every write via `@PrePersist` / `@PreUpdate`

## Hints

- Use `GenerationType.UUID` for the Session ID strategy (Hibernate 6+)
- `Instant` is the right type for timestamps — avoid `LocalDateTime`
- The `session_states` row is optional; a brand-new session has no state yet
- `@MapsId` on `SessionState` means its PK equals the owning session's PK — no separate sequence or UUID gen needed

## Files

- `src/main/java/com/parchie/model/Session.java`
- `src/main/java/com/parchie/model/SessionState.java`
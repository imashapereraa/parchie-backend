# 04 — Session DTOs

Status: done

## What

Create request/response DTOs in `com.parchie.dto`.

V2 collapsed `lastActiveAt`/`ttlDays` into a single `expiresAt`, so the DTOs expose that directly. Clients computing "extend by 7/30/90 days" produce the absolute Instant themselves.

## Requirements

- [ ] `SessionMetadataDto` — returned by all session endpoints
  - Fields: `id` (UUID), `createdAt` (Instant), `expiresAt` (Instant), `locked` (boolean), `hasPassword` (boolean)
  - Static factory method `from(Session)` that maps entity to DTO
  - `hasPassword` should be `true` when `passwordHash != null` — never expose the hash itself
- [ ] `SessionSettingsDto` — request body for PATCH endpoint
  - Fields: `expiresAt` (Instant), `locked` (Boolean), `password` (String)
  - All fields nullable — only provided fields get updated. `password` is plaintext on the wire; the service bcrypt-hashes it before storing.

## Hints

- Use Java `record` for both — they're immutable, concise, and Jackson serializes them out of the box
- Reference types (`Instant`, `Boolean`, `String`) for `SessionSettingsDto` so `null` means "don't change this field"
- The DTO never holds `passwordHash` — output only ever exposes `hasPassword: boolean`

## Depends on

- #01 (Session entity, for the `from()` method)

## Files

- `src/main/java/com/parchie/dto/SessionMetadataDto.java`
- `src/main/java/com/parchie/dto/SessionSettingsDto.java`

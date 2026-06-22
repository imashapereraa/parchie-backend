# 05 — Session Service

Status: done

## What

Create the service layer in `com.parchie.service` with all session business logic.

Schema is V2 (single `sessions` table — see ADR-0005). There is no separate `SessionState` entity; `encryptedState` is a column on `Session`. TTL is `expiresAt`, not `ttl_days + last_active_at`.

## Requirements

- [ ] `createSession()` — saves a new `Session` (defaults applied by `@PrePersist`) and returns it
- [ ] `getSession(UUID)` — returns `Optional<Session>`; filters out expired sessions (treats them as absent)
- [ ] `getSessionOrThrow(UUID)` — calls `getSession`, throws `SessionNotFoundException` if empty
- [ ] `getEncryptedState(UUID)` — returns `session.getEncryptedState()` (may be null if no snapshot yet); throws `SessionNotFoundException` for unknown/expired sessions
- [ ] `updateEncryptedState(UUID, byte[])` — sets the blob, **resets `expiresAt` to `now + 7 days`** (snapshot writes push TTL forward, per `CONTEXT.md`); throws `SessionLockedException` if locked; throws `SessionNotFoundException` if unknown/expired
- [ ] `updateSettings(UUID, SessionSettingsDto)` — partial update of `expiresAt` / `locked` / `password` on `Session`. Password convention: `null` = no change, `""` = clear, non-empty = hash with BCrypt. Returns the updated `Session`. Allowed even when locked (so the session can be unlocked).
- [ ] `cleanupExpiredSessions()` — calls `sessionRepository.deleteAllExpired()`, logs the deleted count, runs on a schedule

## Details

- `@Service` on the class, constructor injection for `SessionRepository`
- `@Transactional` on every write method
- `@Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)` on `cleanupExpiredSessions`. `@EnableScheduling` already lives on `AppConfig`.
- Password hashing: `BCrypt.hashpw(password, BCrypt.gensalt())` from `org.mindrot.jbcrypt` (already in `pom.xml`)
- Snapshot-write TTL reset uses `Instant.now().plus(7, ChronoUnit.DAYS)` to mirror the default in `Session.onCreate()`

## Depends on

- #01 (Session entity — done)
- #02 (SessionRepository — done)
- #03 (exceptions — done)
- #04 (SessionSettingsDto, SessionMetadataDto — done)

## File

`src/main/java/com/parchie/service/SessionService.java`

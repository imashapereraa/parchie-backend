# Architecture Deepening Candidates

## Priority 1: Move TTL & password invariants onto Session
- [ ] Add `extendTtlToAtLeast(Instant)` method to Session (encodes "never shrink" rule)
- [ ] Add `setPasswordFromPlaintext(String)` method to Session (encodes BCrypt hashing)
- [ ] Add `clearPassword()` method to Session
- [ ] Add `verifyPassword(String): boolean` method to Session
- [ ] Extract `TTL_DAYS = 7` constant to Session (currently duplicated)
- [ ] Remove raw `setExpiresAt()` and `setPasswordHash()` setters from Session
- [ ] Update `SessionService.updateEncryptedState()` to use `extendTtlToAtLeast()`
- [ ] Update `SessionService.updateSettings()` to use new Session password methods
- [ ] Add unit tests for TTL extension and password logic in `SessionTest`
- [ ] Remove corresponding logic from `SessionServiceTest`
- **Benefits:** Locality for TTL & password invariants; testable without Spring; foundation for password-gate feature

---

## Priority 2: Extract "Relay channel" from SessionRelayHandler
- [ ] Create `RelayChannel` class: owns `Set<WebSocketSession>` + `broadcast(BinaryMessage, exceptSender)`
- [ ] Update `SessionRelayHandler` to hold `Map<UUID, RelayChannel>` instead of `peersBySession`
- [ ] Cache Session id in `WebSocketSession.getAttributes()` at handshake, read it in callbacks
- [ ] Remove URI parsing from `handleBinaryMessage()` and `afterConnectionClosed()`
- [ ] Update `RelayChannel.broadcast()` to handle broken peer resilience
- [ ] Extract relay-channel logic tests to plain POJO unit tests (no @SpringBootTest)
- [ ] Update `SessionRelayHandlerTest` to use the new structure
- **Benefits:** Isolates broadcast logic; testable without Spring; foundation for Presence broadcast and lock-based peer kicks

---

## Priority 3: Single CORS configuration source
- [ ] Create `CorsProperties` `@ConfigurationProperties` class
- [ ] Define single default for `allowed-origins` (recommend `http://localhost:5173` for dev)
- [ ] Inject `CorsProperties` into both `AppConfig` and `WebSocketConfig`
- [ ] Remove `@Value` annotations from both files
- [ ] Add config validation (non-empty origins)
- **Benefits:** Prevents operator misconfiguration; single source of truth for CORS policy

---

## Priority 4: "Live session" lookups at repository seam (optional/minor)
- [ ] Add `findLiveSession(UUID): Optional<Session>` to `SessionRepository` with SQL WHERE clause
- [ ] Update `SessionService.getSession()` to call `findLiveSession()` instead of `findById().filter()`
- [ ] Update callers that used `findById()` directly to use `findLiveSession()`
- **Benefits:** Closes footgun; tiny perf win; "live" rule lives at data-access layer

---

## Execution notes
- **Recommended order:** 1 → 2 → 3 → 4 (by leverage and dependency)
- **1 is self-contained** and unblocks the password-gate feature
- **2 depends on no others** but is easier after 1 (no urgent dependencies)
- **3 is orthogonal** to 1 & 2; can run in parallel
- **4 is strictly optional** and safe to defer
# 07 — WebSocket Relay Handler

Status: done

## What

Create the binary WebSocket handler in `com.parchie.websocket` that relays Yjs sync messages between clients in the same session.

## Requirements

- [x] Extend `BinaryWebSocketHandler` (not `TextWebSocketHandler` — Yjs sends binary frames)
- [x] Maintain a `ConcurrentHashMap<UUID, Set<WebSocketSession>>` mapping session IDs to connected clients
- [x] `afterConnectionEstablished` — extract session ID from the URL path, validate it exists via `SessionService`, add the connection to the map (closes with `POLICY_VIOLATION` on unknown)
- [x] `handleBinaryMessage` — broadcast the incoming message to all OTHER peers in the same session (not back to the sender)
- [x] `afterConnectionClosed` — remove the connection from the map, clean up empty sets
- [x] Register as a Spring `@Component`

Covered by `SessionRelayHandlerTest` (TDD): connect-valid, reject-unknown, broadcast-no-echo, cross-session-isolation, peer-cleanup-on-close, broken-peer-resilience.

## Details

- The WebSocket URL is `/ws/sessions/{id}` — extract the session ID from `wsSession.getUri().getPath()`
- Use `ConcurrentHashMap.newKeySet()` for thread-safe connection sets
- Wrap `peer.sendMessage()` in try/catch per peer — one broken connection shouldn't crash the broadcast
- After creating this, update `WebSocketConfig.java` to inject and register it

## Depends on

- #05 (SessionService for validation)

## Files

- `src/main/java/com/parchie/websocket/SessionRelayHandler.java`
- `src/main/java/com/parchie/websocket/WebSocketConfig.java` (update existing)

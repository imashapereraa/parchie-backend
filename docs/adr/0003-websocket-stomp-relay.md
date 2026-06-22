# ADR-0003: Raw WebSocket for real-time relay

## Status

Accepted (supersedes earlier STOMP decision)

## Context

Parchie needs real-time bidirectional communication for Yjs CRDT sync and cursor presence. Options considered: raw WebSocket, STOMP over WebSocket, SSE + polling, Socket.IO (not native to Spring).

The client uses `y-websocket`, which speaks Yjs's own binary sync protocol. STOMP is a text-oriented framing protocol — wrapping binary Yjs messages in STOMP frames adds unnecessary encoding overhead and complexity with no benefit, since we don't need STOMP's topic-based routing (session routing is handled by URL path).

## Decision

Use raw Spring WebSocket (`WebSocketHandler`) instead of STOMP.

- Each session maps to a WebSocket endpoint (e.g., `/ws/session/{id}`).
- The handler maintains a map of session ID to connected clients and broadcasts incoming binary frames to all other participants in the same session.
- No message parsing — the server forwards encrypted Yjs binary blobs as-is.
- This aligns with the relay-only invariant: the backend is a dumb pipe for encrypted bytes.

## Consequences

- **Simpler.** No STOMP configuration, no message broker, no `@MessageMapping`. Just a `WebSocketHandler` that routes by session.
- **Binary-native.** Yjs sync messages stay as binary frames end-to-end. No base64 encoding or text framing.
- **Manual session routing.** We manage the session-to-connections map ourselves instead of relying on STOMP topics. Straightforward for a single-server setup.
- **Scalability ceiling unchanged.** Horizontal scaling still requires an external pub/sub layer (Redis, etc.). Acceptable for a learning project on a single VPS.

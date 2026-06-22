package com.parchie.websocket;

import com.parchie.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRelayHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SessionRelayHandler.class);
    private static final String ROOM_KEY_ATTR = "parchie.roomKey";

    private final SessionRepository sessionRepository;
    private final ConcurrentHashMap<String, Set<WebSocketSession>> peersByRoom = new ConcurrentHashMap<>();

    public SessionRelayHandler(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomKey = resolveRoomKey(session);
        if (roomKey == null) {
            try { session.close(CloseStatus.POLICY_VIOLATION.withReason("Unknown session")); } catch (Exception ignored) {}
            return;
        }
        session.getAttributes().put(ROOM_KEY_ATTR, roomKey);
        peersByRoom.computeIfAbsent(roomKey, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession sender, BinaryMessage message) {
        String roomKey = (String) sender.getAttributes().get(ROOM_KEY_ATTR);
        if (roomKey == null) return;
        Set<WebSocketSession> peers = peersByRoom.get(roomKey);
        if (peers == null) return;
        for (WebSocketSession peer : peers) {
            if (peer == sender) continue;
            try {
                peer.sendMessage(message);
            } catch (Exception e) {
                log.warn("Failed to relay frame to peer {} in room {}: {}", peer.getId(), roomKey, e.toString());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomKey = (String) session.getAttributes().get(ROOM_KEY_ATTR);
        if (roomKey == null) return;
        peersByRoom.computeIfPresent(roomKey, (r, peers) -> {
            peers.remove(session);
            return peers.isEmpty() ? null : peers;
        });
    }

    public int peerCount(String idOrSlug) {
        String roomKey = resolveRoomKey(idOrSlug);
        if (roomKey == null) return 0;
        Set<WebSocketSession> peers = peersByRoom.get(roomKey);
        return peers == null ? 0 : peers.size();
    }

    public void closeRoom(String roomKey, CloseStatus reason) {
        Set<WebSocketSession> peers = peersByRoom.remove(roomKey);
        if (peers == null) return;
        for (WebSocketSession peer : peers) {
            try {
                peer.close(reason);
            } catch (Exception e) {
                log.warn("Failed to close peer {} in room {}: {}", peer.getId(), roomKey, e.toString());
            }
        }
    }

    private String resolveRoomKey(WebSocketSession session) {
        if (session.getUri() == null) return null;
        String path = session.getUri().getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) return null;
        return resolveRoomKey(path.substring(lastSlash + 1));
    }

    private String resolveRoomKey(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.isBlank()) return null;
        UUID parsed = tryParseUuid(idOrSlug);
        if (parsed != null) {
            return sessionRepository.findById(parsed)
                    .filter(s -> !s.isExpired())
                    .map(s -> s.getId().toString())
                    .orElse(null);
        }
        return sessionRepository.findBySlug(idOrSlug)
                .filter(s -> !s.isExpired())
                .map(s -> s.getId().toString())
                .orElse(null);
    }

    private UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

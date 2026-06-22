package com.parchie.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRelayHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SessionRelayHandler.class);

    private final ConcurrentHashMap<String, Set<WebSocketSession>> peersByRoom = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String room = parseRoom(session);
        if (room == null || room.isBlank()) {
            try { session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing room")); } catch (Exception ignored) {}
            return;
        }
        peersByRoom.computeIfAbsent(room, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession sender, BinaryMessage message) {
        String room = parseRoom(sender);
        if (room == null) return;
        Set<WebSocketSession> peers = peersByRoom.get(room);
        if (peers == null) return;
        for (WebSocketSession peer : peers) {
            if (peer == sender) continue;
            try {
                peer.sendMessage(message);
            } catch (Exception e) {
                log.warn("Failed to relay frame to peer {} in room {}: {}", peer.getId(), room, e.toString());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String room = parseRoom(session);
        if (room == null) return;
        peersByRoom.computeIfPresent(room, (r, peers) -> {
            peers.remove(session);
            return peers.isEmpty() ? null : peers;
        });
    }

    public int peerCount(String room) {
        Set<WebSocketSession> peers = peersByRoom.get(room);
        return peers == null ? 0 : peers.size();
    }

    private String parseRoom(WebSocketSession session) {
        if (session.getUri() == null) return null;
        String path = session.getUri().getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) return null;
        return path.substring(lastSlash + 1);
    }
}

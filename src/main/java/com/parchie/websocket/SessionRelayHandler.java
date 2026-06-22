package com.parchie.websocket;

import com.parchie.model.Session;
import com.parchie.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRelayHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SessionRelayHandler.class);

    private final SessionService sessionService;
    private final ConcurrentHashMap<UUID, Set<WebSocketSession>> peersBySession = new ConcurrentHashMap<>();

    public SessionRelayHandler(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID sessionId = parseSessionId(session);
        if (sessionId == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Unknown session"));
            return;
        }
        Optional<Session> parchieSession = sessionService.getSession(sessionId);
        if (parchieSession.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Unknown session"));
            return;
        }
        if (!parchieSession.get().passwordMatches(parsePassword(session))) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid password"));
            return;
        }
        peersBySession
                .computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession sender, BinaryMessage message) {
        UUID sessionId = parseSessionId(sender);
        if (sessionId == null) return;
        Set<WebSocketSession> peers = peersBySession.get(sessionId);
        if (peers == null) return;
        for (WebSocketSession peer : peers) {
            if (peer == sender) continue;
            try {
                peer.sendMessage(message);
            } catch (Exception e) {
                log.warn("Failed to relay frame to peer {} in session {}: {}",
                        peer.getId(), sessionId, e.toString());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID sessionId = parseSessionId(session);
        if (sessionId == null) return;
        peersBySession.computeIfPresent(sessionId, (id, peers) -> {
            peers.remove(session);
            return peers.isEmpty() ? null : peers;
        });
    }

    public int peerCount(UUID sessionId) {
        Set<WebSocketSession> peers = peersBySession.get(sessionId);
        return peers == null ? 0 : peers.size();
    }

    private UUID parseSessionId(WebSocketSession session) {
        if (session.getUri() == null) return null;
        String path = session.getUri().getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) return null;
        try {
            return UUID.fromString(path.substring(lastSlash + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String parsePassword(WebSocketSession session) {
        if (session.getUri() == null) return null;
        String query = session.getUri().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "password".equals(kv[0])) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}

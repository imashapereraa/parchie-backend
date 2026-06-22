package com.parchie.websocket;

import com.parchie.model.Session;
import com.parchie.repository.SessionRepository;
import com.parchie.service.SessionService;
import org.mindrot.jbcrypt.BCrypt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class SessionRelayHandlerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    SessionService sessionService;

    @Autowired
    SessionRepository sessionRepository;

    @Autowired
    SessionRelayHandler handler;

    @AfterEach
    void cleanup() {
        sessionRepository.deleteAll();
    }

    private RecordingClient connect(UUID sessionId) throws Exception {
        return connectTo("ws://localhost:" + port + "/ws/sessions/" + sessionId);
    }

    private RecordingClient connectWithPassword(UUID sessionId, String password) throws Exception {
        return connectTo("ws://localhost:" + port + "/ws/sessions/" + sessionId + "?password=" + password);
    }

    private RecordingClient connectTo(String url) throws Exception {
        RecordingClient client = new RecordingClient();
        StandardWebSocketClient ws = new StandardWebSocketClient();
        WebSocketSession session = ws.execute(client, new WebSocketHttpHeaders(), URI.create(url))
                .get(5, TimeUnit.SECONDS);
        client.session = session;
        return client;
    }

    private void awaitPeers(UUID sessionId, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (handler.peerCount(sessionId) == expected) return;
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        throw new AssertionError("Timed out waiting for " + expected + " peers in session " + sessionId
                + " (have " + handler.peerCount(sessionId) + ")");
    }

    private static byte[] payload(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        return bytes;
    }

    @Test
    void connect_succeedsForExistingSession() throws Exception {
        Session created = sessionService.createSession();

        RecordingClient client = connect(created.getId());

        assertTrue(client.openLatch.await(2, TimeUnit.SECONDS), "Connection should open");
        assertTrue(client.session.isOpen(), "Session should remain open for valid id");
        client.session.close();
    }

    @Test
    void connect_isClosedForUnknownSession() throws Exception {
        RecordingClient client = connect(UUID.randomUUID());

        CloseStatus status = client.closeFuture.get(5, TimeUnit.SECONDS);
        assertNotEquals(CloseStatus.NORMAL.getCode(), status.getCode(),
                "Server should reject unknown session with non-normal close");
    }

    @Test
    void binaryMessage_broadcastsToOtherPeersOnly() throws Exception {
        Session s = sessionService.createSession();
        RecordingClient a = connect(s.getId());
        RecordingClient b = connect(s.getId());
        assertTrue(a.openLatch.await(2, TimeUnit.SECONDS));
        assertTrue(b.openLatch.await(2, TimeUnit.SECONDS));
        awaitPeers(s.getId(), 2);

        byte[] msg = payload(1, 2, 3, 4);
        a.session.sendMessage(new BinaryMessage(msg));

        byte[] receivedByB = b.received.poll(5, TimeUnit.SECONDS);
        assertArrayEquals(msg, receivedByB, "B should receive A's frame verbatim");

        byte[] echoToA = a.received.poll(500, TimeUnit.MILLISECONDS);
        assertNull(echoToA, "Sender must not receive its own frame");

        a.session.close();
        b.session.close();
    }

    @Test
    void messages_areIsolatedBetweenSessions() throws Exception {
        Session s1 = sessionService.createSession();
        Session s2 = sessionService.createSession();

        RecordingClient a = connect(s1.getId());
        RecordingClient b = connect(s1.getId());
        RecordingClient c = connect(s2.getId());
        assertTrue(a.openLatch.await(2, TimeUnit.SECONDS));
        assertTrue(b.openLatch.await(2, TimeUnit.SECONDS));
        assertTrue(c.openLatch.await(2, TimeUnit.SECONDS));
        awaitPeers(s1.getId(), 2);
        awaitPeers(s2.getId(), 1);

        byte[] msg = payload(42, 7);
        a.session.sendMessage(new BinaryMessage(msg));

        byte[] receivedByB = b.received.poll(5, TimeUnit.SECONDS);
        assertArrayEquals(msg, receivedByB);

        byte[] leakedToC = c.received.poll(500, TimeUnit.MILLISECONDS);
        assertNull(leakedToC, "Session 2 must not receive session 1's frames");

        a.session.close();
        b.session.close();
        c.session.close();
    }

    @Test
    void closingConnection_removesPeerFromBroadcast() throws Exception {
        Session s = sessionService.createSession();
        RecordingClient a = connect(s.getId());
        RecordingClient b = connect(s.getId());
        assertTrue(a.openLatch.await(2, TimeUnit.SECONDS));
        assertTrue(b.openLatch.await(2, TimeUnit.SECONDS));
        awaitPeers(s.getId(), 2);

        b.session.close();
        assertNotNull(b.closeFuture.get(5, TimeUnit.SECONDS));
        awaitPeers(s.getId(), 1);

        RecordingClient c = connect(s.getId());
        assertTrue(c.openLatch.await(2, TimeUnit.SECONDS));
        awaitPeers(s.getId(), 2);

        byte[] msg = payload(9, 9);
        a.session.sendMessage(new BinaryMessage(msg));

        assertArrayEquals(msg, c.received.poll(5, TimeUnit.SECONDS),
                "Newly joined peer should receive the broadcast");

        a.session.close();
        c.session.close();
    }

    @Nested
    class BrokenPeerResilience {

        @Test
        void brokenPeer_doesNotPreventDeliveryToHealthyPeers() throws Exception {
            Session s = sessionService.createSession();
            String path = "/ws/sessions/" + s.getId();

            WebSocketSession sender = mockSession("sender", path);
            WebSocketSession broken = mockSession("broken", path);
            WebSocketSession healthy = mockSession("healthy", path);

            doThrow(new IOException("simulated")).when(broken).sendMessage(any());

            handler.afterConnectionEstablished(sender);
            handler.afterConnectionEstablished(broken);
            handler.afterConnectionEstablished(healthy);

            BinaryMessage msg = new BinaryMessage(ByteBuffer.wrap(payload(5, 5, 5)));
            assertDoesNotThrow(() -> handler.handleMessage(sender, msg));

            verify(broken, times(1)).sendMessage(any());
            verify(healthy, times(1)).sendMessage(any());
            verify(sender, never()).sendMessage(any());
        }

        private WebSocketSession mockSession(String id, String path) {
            WebSocketSession ws = mock(WebSocketSession.class);
            when(ws.getId()).thenReturn(id);
            when(ws.isOpen()).thenReturn(true);
            when(ws.getUri()).thenReturn(URI.create("ws://localhost" + path));
            return ws;
        }
    }

    @Test
    void connect_closedForPasswordProtectedSession_withNoPassword() throws Exception {
        Session s = sessionService.createSession();
        s.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        sessionRepository.save(s);

        RecordingClient client = connect(s.getId());

        CloseStatus status = client.closeFuture.get(5, TimeUnit.SECONDS);
        assertNotEquals(CloseStatus.NORMAL.getCode(), status.getCode(),
                "Server should reject connection when password is required but not provided");
    }

    @Test
    void connect_closedForPasswordProtectedSession_withWrongPassword() throws Exception {
        Session s = sessionService.createSession();
        s.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        sessionRepository.save(s);

        RecordingClient client = connectWithPassword(s.getId(), "wrong");

        CloseStatus status = client.closeFuture.get(5, TimeUnit.SECONDS);
        assertNotEquals(CloseStatus.NORMAL.getCode(), status.getCode(),
                "Server should reject connection with wrong password");
    }

    @Test
    void connect_succeedsForPasswordProtectedSession_withCorrectPassword() throws Exception {
        Session s = sessionService.createSession();
        s.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        sessionRepository.save(s);

        RecordingClient client = connectWithPassword(s.getId(), "secret");

        assertTrue(client.openLatch.await(2, TimeUnit.SECONDS), "Connection should open with correct password");
        assertTrue(client.session.isOpen());
        client.session.close();
    }

    private static class RecordingClient extends BinaryWebSocketHandler {
        final LinkedBlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
        final java.util.concurrent.CountDownLatch openLatch = new java.util.concurrent.CountDownLatch(1);
        final CompletableFuture<CloseStatus> closeFuture = new CompletableFuture<>();
        WebSocketSession session;

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            openLatch.countDown();
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            ByteBuffer buf = message.getPayload();
            byte[] copy = new byte[buf.remaining()];
            buf.get(copy);
            received.add(copy);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closeFuture.complete(status);
        }
    }
}

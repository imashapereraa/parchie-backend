package com.parchie.websocket;

import com.parchie.model.Session;
import com.parchie.repository.SessionRepository;
import org.junit.jupiter.api.AfterEach;
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
import java.util.HashMap;
import java.util.Map;
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
    SessionRelayHandler handler;

    @Autowired
    SessionRepository sessionRepository;

    @AfterEach
    void cleanup() {
        sessionRepository.deleteAll();
    }

    private Session newSession(String slug) {
        Session s = new Session();
        s.setSlug(slug);
        return sessionRepository.save(s);
    }

    private RecordingClient connect(String room) throws Exception {
        RecordingClient client = new RecordingClient();
        StandardWebSocketClient ws = new StandardWebSocketClient();
        WebSocketSession session = ws.execute(client, new WebSocketHttpHeaders(),
                URI.create("ws://localhost:" + port + "/ws/sessions/" + room))
                .get(5, TimeUnit.SECONDS);
        client.session = session;
        return client;
    }

    private void awaitPeers(String room, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (handler.peerCount(room) == expected) return;
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        throw new AssertionError("Timed out waiting for " + expected + " peers in room " + room
                + " (have " + handler.peerCount(room) + ")");
    }

    private static byte[] payload(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        return bytes;
    }

    @Test
    void connect_acceptsValidUuid() throws Exception {
        Session s = newSession("uuid-room-slug");
        RecordingClient client = connect(s.getId().toString());
        assertTrue(client.openLatch.await(2, TimeUnit.SECONDS), "Connection should open for known UUID");
        assertTrue(client.session.isOpen());
        client.session.close();
    }

    @Test
    void connect_acceptsValidSlug() throws Exception {
        newSession("slug-only-room");
        RecordingClient client = connect("slug-only-room");
        assertTrue(client.openLatch.await(2, TimeUnit.SECONDS), "Connection should open for known slug");
        assertTrue(client.session.isOpen());
        client.session.close();
    }

    @Test
    void connect_rejectsUnknownRoom() throws Exception {
        RecordingClient client = connect("no-such-room-xyz");
        CloseStatus close = client.closeFuture.get(5, TimeUnit.SECONDS);
        assertEquals(CloseStatus.POLICY_VIOLATION.getCode(), close.getCode(),
                "Unknown rooms should be closed with POLICY_VIOLATION");
    }

    @Test
    void slugAndUuid_routeToSameRoom() throws Exception {
        Session s = newSession("dual-route-test");

        RecordingClient byUuid = connect(s.getId().toString());
        RecordingClient bySlug = connect("dual-route-test");
        assertTrue(byUuid.openLatch.await(2, TimeUnit.SECONDS));
        assertTrue(bySlug.openLatch.await(2, TimeUnit.SECONDS));
        awaitPeers(s.getId().toString(), 2);

        byte[] msg = payload(7, 8, 9);
        byUuid.session.sendMessage(new BinaryMessage(msg));

        byte[] received = bySlug.received.poll(5, TimeUnit.SECONDS);
        assertArrayEquals(msg, received,
                "Peer joined by slug should receive frame from peer joined by UUID");

        byUuid.session.close();
        bySlug.session.close();
    }

    @Test
    void binaryMessage_broadcastsToOtherPeersOnly() throws Exception {
        Session s = newSession("broadcast-room");
        RecordingClient a = connect(s.getId().toString());
        RecordingClient b = connect(s.getId().toString());
        assertTrue(a.openLatch.await(2, TimeUnit.SECONDS));
        assertTrue(b.openLatch.await(2, TimeUnit.SECONDS));
        awaitPeers(s.getId().toString(), 2);

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
    void messages_areIsolatedBetweenRooms() throws Exception {
        Session s1 = newSession("isolate-room-1");
        Session s2 = newSession("isolate-room-2");

        RecordingClient a = connect(s1.getId().toString());
        RecordingClient b = connect(s1.getId().toString());
        RecordingClient c = connect(s2.getId().toString());
        assertTrue(a.openLatch.await(2, TimeUnit.SECONDS));
        assertTrue(b.openLatch.await(2, TimeUnit.SECONDS));
        assertTrue(c.openLatch.await(2, TimeUnit.SECONDS));
        awaitPeers(s1.getId().toString(), 2);
        awaitPeers(s2.getId().toString(), 1);

        byte[] msg = payload(42, 7);
        a.session.sendMessage(new BinaryMessage(msg));

        byte[] receivedByB = b.received.poll(5, TimeUnit.SECONDS);
        assertArrayEquals(msg, receivedByB);

        byte[] leakedToC = c.received.poll(500, TimeUnit.MILLISECONDS);
        assertNull(leakedToC, "Other session must not receive room 1's frames");

        a.session.close();
        b.session.close();
        c.session.close();
    }

    @Test
    void closingConnection_removesPeerFromBroadcast() throws Exception {
        Session s = newSession("close-room");
        RecordingClient a = connect(s.getId().toString());
        RecordingClient b = connect(s.getId().toString());
        assertTrue(a.openLatch.await(2, TimeUnit.SECONDS));
        assertTrue(b.openLatch.await(2, TimeUnit.SECONDS));
        awaitPeers(s.getId().toString(), 2);

        b.session.close();
        assertNotNull(b.closeFuture.get(5, TimeUnit.SECONDS));
        awaitPeers(s.getId().toString(), 1);

        RecordingClient c = connect(s.getId().toString());
        assertTrue(c.openLatch.await(2, TimeUnit.SECONDS));
        awaitPeers(s.getId().toString(), 2);

        byte[] msg = payload(9, 9);
        a.session.sendMessage(new BinaryMessage(msg));

        assertArrayEquals(msg, c.received.poll(5, TimeUnit.SECONDS),
                "Newly joined peer should receive the broadcast");

        a.session.close();
        c.session.close();
    }

    @Test
    void closeRoom_kicksAllConnectedPeers() throws Exception {
        Session s = newSession("close-room-sweep");
        String roomId = s.getId().toString();

        RecordingClient a = connect(roomId);
        RecordingClient b = connect(roomId);
        assertTrue(a.openLatch.await(2, TimeUnit.SECONDS));
        assertTrue(b.openLatch.await(2, TimeUnit.SECONDS));
        awaitPeers(roomId, 2);

        handler.closeRoom(roomId, CloseStatus.GOING_AWAY.withReason("session expired"));

        CloseStatus closeA = a.closeFuture.get(5, TimeUnit.SECONDS);
        CloseStatus closeB = b.closeFuture.get(5, TimeUnit.SECONDS);
        assertEquals(CloseStatus.GOING_AWAY.getCode(), closeA.getCode(),
                "Peer A should receive GOING_AWAY close");
        assertEquals(CloseStatus.GOING_AWAY.getCode(), closeB.getCode(),
                "Peer B should receive GOING_AWAY close");
        assertEquals(0, handler.peerCount(roomId), "Room should be empty after closeRoom");
    }

    @Test
    void brokenPeer_doesNotPreventDeliveryToHealthyPeers() throws Exception {
        Session s = newSession("broken-peer-room");
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
        // attribute map is read & written by the handler to cache the resolved room key.
        Map<String, Object> attrs = new HashMap<>();
        when(ws.getAttributes()).thenReturn(attrs);
        return ws;
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

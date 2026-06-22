package com.parchie.bdd;

import com.parchie.websocket.SessionRelayHandler;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class WebSocketRelaySteps {

    @Autowired
    ScenarioContext context;

    @Autowired
    SessionRelayHandler handler;

    @LocalServerPort
    int port;

    private final ConcurrentHashMap<String, CompletableFuture<CloseStatus>> closeFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> connectedSessions = new ConcurrentHashMap<>();

    private void connect(String clientName, UUID sessionId) throws Exception {
        BlockingQueue<byte[]> inbox = new LinkedBlockingQueue<>();
        CompletableFuture<CloseStatus> closeFuture = new CompletableFuture<>();
        closeFutures.put(clientName, closeFuture);

        BinaryWebSocketHandler clientHandler = new BinaryWebSocketHandler() {
            @Override
            protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
                ByteBuffer buf = message.getPayload();
                byte[] copy = new byte[buf.remaining()];
                buf.get(copy);
                inbox.add(copy);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closeFuture.complete(status);
            }
        };

        StandardWebSocketClient ws = new StandardWebSocketClient();
        WebSocketSession session = ws.execute(
                        clientHandler,
                        new WebSocketHttpHeaders(),
                        URI.create("ws://localhost:" + port + "/ws/sessions/" + sessionId))
                .get(5, TimeUnit.SECONDS);

        context.putClient(clientName, session, inbox);
        connectedSessions.put(clientName, sessionId);
    }

    private void awaitPeers(UUID sessionId, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (handler.peerCount(sessionId) == expected) return;
            try { Thread.sleep(10); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); return;
            }
        }
        throw new AssertionError("Timed out waiting for " + expected + " peers in session " + sessionId
                + " (have " + handler.peerCount(sessionId) + ")");
    }

    @Given("client {string} connects to {string}")
    public void clientConnectsTo(String clientName, String sessionAlias) throws Exception {
        UUID sessionId = context.sessionId(sessionAlias);
        connect(clientName, sessionId);
        long peers = connectedSessions.values().stream().filter(sessionId::equals).count();
        awaitPeers(sessionId, (int) peers);
    }

    @When("client {string} connects to a random session id")
    public void clientConnectsToRandom(String clientName) throws Exception {
        connect(clientName, UUID.randomUUID());
    }

    @When("client {string} sends bytes {word}")
    public void clientSendsBytes(String clientName, String hex) throws Exception {
        context.client(clientName).sendMessage(new BinaryMessage(HexBytes.parse(hex)));
    }

    @Then("client {string} receives bytes {word} within {int} seconds")
    public void clientReceivesBytes(String clientName, String hex, int seconds) throws Exception {
        byte[] received = context.inbox(clientName).poll(seconds, TimeUnit.SECONDS);
        assertArrayEquals(HexBytes.parse(hex), received,
                "client " + clientName + " did not receive expected bytes");
    }

    @Then("client {string} receives nothing within {int} ms")
    public void clientReceivesNothing(String clientName, int ms) throws Exception {
        byte[] received = context.inbox(clientName).poll(ms, TimeUnit.MILLISECONDS);
        assertNull(received, "client " + clientName + " unexpectedly received bytes");
    }

    @Then("client {string} is closed by the server within {int} seconds")
    public void clientIsClosed(String clientName, int seconds) throws Exception {
        CloseStatus status = closeFutures.get(clientName).get(seconds, TimeUnit.SECONDS);
        assertNotNull(status);
        assertNotEquals(CloseStatus.NORMAL.getCode(), status.getCode(),
                "expected non-normal close, got " + status);
    }
}

package com.parchie.bdd;

import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@ScenarioScope
public class ScenarioContext {

    private final Map<String, UUID> sessionIds = new LinkedHashMap<>();
    private final Map<String, WebSocketSession> wsClients = new LinkedHashMap<>();
    private final Map<String, BlockingQueue<byte[]>> wsInboxes = new LinkedHashMap<>();
    private final List<AutoCloseable> closeables = new ArrayList<>();

    private int lastStatus;
    private String lastBody;
    private byte[] lastBytes;

    public void putSession(String alias, UUID id) { sessionIds.put(alias, id); }
    public UUID sessionId(String alias) { return sessionIds.get(alias); }

    public void putClient(String alias, WebSocketSession client, BlockingQueue<byte[]> inbox) {
        wsClients.put(alias, client);
        wsInboxes.put(alias, inbox);
    }
    public WebSocketSession client(String alias) { return wsClients.get(alias); }
    public BlockingQueue<byte[]> inbox(String alias) {
        return wsInboxes.computeIfAbsent(alias, k -> new LinkedBlockingQueue<>());
    }

    public void register(AutoCloseable c) { closeables.add(c); }

    public void setLastStatus(int status) { this.lastStatus = status; }
    public int lastStatus() { return lastStatus; }

    public void setLastBody(String body) { this.lastBody = body; }
    public String lastBody() { return lastBody; }

    public void setLastBytes(byte[] bytes) { this.lastBytes = bytes; }
    public byte[] lastBytes() { return lastBytes; }

    public void closeAll() {
        for (WebSocketSession c : wsClients.values()) {
            try { if (c.isOpen()) c.close(); } catch (Exception ignored) {}
        }
        for (AutoCloseable c : closeables) {
            try { c.close(); } catch (Exception ignored) {}
        }
    }
}

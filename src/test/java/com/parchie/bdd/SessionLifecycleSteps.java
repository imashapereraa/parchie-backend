package com.parchie.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SessionLifecycleSteps {

    @Autowired
    ScenarioContext context;

    @Autowired
    ObjectMapper mapper;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private URI sessionsUri() {
        return URI.create("http://localhost:" + port + "/api/sessions");
    }

    private URI sessionUri(UUID id) {
        return URI.create("http://localhost:" + port + "/api/sessions/" + id);
    }

    private void send(HttpRequest request) throws Exception {
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        context.setLastStatus(response.statusCode());
        context.setLastBytes(response.body());
        context.setLastBody(response.body() == null ? "" : new String(response.body()));
    }

    @When("I create a session as {string}")
    public void createSession(String alias) throws Exception {
        send(HttpRequest.newBuilder(sessionsUri()).POST(HttpRequest.BodyPublishers.noBody()).build());
        JsonNode json = mapper.readTree(context.lastBody());
        context.putSession(alias, UUID.fromString(json.get("id").asText()));
    }

    @Given("a session {string} exists")
    public void sessionExists(String alias) throws Exception {
        createSession(alias);
    }

    @When("I fetch session with id {string}")
    public void fetchSessionById(String id) throws Exception {
        send(HttpRequest.newBuilder(sessionUri(UUID.fromString(id))).GET().build());
    }

    @When("I fetch session {string}")
    public void fetchSessionByAlias(String alias) throws Exception {
        send(HttpRequest.newBuilder(sessionUri(context.sessionId(alias))).GET().build());
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int status) {
        assertEquals(status, context.lastStatus(), "body=" + context.lastBody());
    }

    @Then("the response has session metadata")
    public void responseHasMetadata() throws Exception {
        JsonNode json = mapper.readTree(context.lastBody());
        assertNotNull(json.get("id"));
        assertNotNull(json.get("createdAt"));
        assertNotNull(json.get("expiresAt"));
    }

    @And("the metadata says {string} is {string}")
    public void metadataSays(String field, String expected) throws Exception {
        JsonNode json = mapper.readTree(context.lastBody());
        assertEquals(expected, json.get(field).asText());
    }

    @And("the expiry of {string} is approximately {int} days from now")
    public void expiryApproximately(String alias, int days) throws Exception {
        JsonNode json = mapper.readTree(context.lastBody());
        Instant expiresAt = Instant.parse(json.get("expiresAt").asText());
        Instant target = Instant.now().plus(days, ChronoUnit.DAYS);
        Duration delta = Duration.between(target, expiresAt).abs();
        assertTrue(delta.toMinutes() < 5,
                "expiresAt=" + expiresAt + " not within 5 min of " + target);
    }
}

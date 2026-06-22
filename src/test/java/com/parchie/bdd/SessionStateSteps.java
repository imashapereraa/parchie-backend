package com.parchie.bdd;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class SessionStateSteps {

    @Autowired
    ScenarioContext context;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private URI stateUri(UUID id) {
        return URI.create("http://localhost:" + port + "/api/sessions/" + id + "/state");
    }

    private void send(HttpRequest request) throws Exception {
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        context.setLastStatus(response.statusCode());
        context.setLastBytes(response.body());
    }

    @When("I GET the encrypted state of {string}")
    public void getStateOfAlias(String alias) throws Exception {
        send(HttpRequest.newBuilder(stateUri(context.sessionId(alias))).GET().build());
    }

    @When("I GET the encrypted state of session id {string}")
    public void getStateOfId(String id) throws Exception {
        send(HttpRequest.newBuilder(stateUri(UUID.fromString(id))).GET().build());
    }

    @When("I PUT the bytes {word} to the encrypted state of {string}")
    public void putStateOfAlias(String hex, String alias) throws Exception {
        byte[] body = HexBytes.parse(hex);
        send(HttpRequest.newBuilder(stateUri(context.sessionId(alias)))
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build());
    }

    @Given("session {string} is locked")
    public void lockSession(String alias) throws Exception {
        URI uri = URI.create("http://localhost:" + port + "/api/sessions/" + context.sessionId(alias));
        HttpResponse<byte[]> response = http.send(
                HttpRequest.newBuilder(uri)
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"locked\":true}"))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed to lock session: " + response.statusCode());
        }
    }

    @Then("the response bytes are {word}")
    public void responseBytesAre(String hex) {
        assertArrayEquals(HexBytes.parse(hex), context.lastBytes());
    }

    @And("the response is empty")
    public void responseIsEmpty() {
        byte[] b = context.lastBytes();
        if (b != null && b.length > 0) {
            throw new AssertionError("Expected empty response but got " + b.length + " bytes");
        }
    }
}

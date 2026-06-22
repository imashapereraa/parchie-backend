package com.parchie.bdd;

import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

public class SessionSettingsSteps {

    @Autowired
    ScenarioContext context;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private URI sessionUri(UUID id) {
        return URI.create("http://localhost:" + port + "/api/sessions/" + id);
    }

    private void patch(UUID id, String body) throws Exception {
        HttpResponse<byte[]> response = http.send(
                HttpRequest.newBuilder(sessionUri(id))
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        context.setLastStatus(response.statusCode());
        context.setLastBytes(response.body());
        context.setLastBody(response.body() == null ? "" : new String(response.body()));
    }

    @When("I PATCH {string} with locked=true")
    public void patchLockedTrue(String alias) throws Exception {
        patch(context.sessionId(alias), "{\"locked\":true}");
    }

    @When("I PATCH {string} with password {string}")
    public void patchPassword(String alias, String password) throws Exception {
        patch(context.sessionId(alias), "{\"password\":\"" + password + "\"}");
    }

    @When("I PATCH session id {string} with locked=true")
    public void patchUnknownLockedTrue(String id) throws Exception {
        patch(UUID.fromString(id), "{\"locked\":true}");
    }
}

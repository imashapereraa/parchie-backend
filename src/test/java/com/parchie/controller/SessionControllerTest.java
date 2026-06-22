package com.parchie.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parchie.dto.SessionSettingsDto;
import com.parchie.model.Session;
import com.parchie.repository.SessionRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class SessionControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SessionRepository sessionRepository;

    @Autowired
    ObjectMapper objectMapper;

    @AfterEach
    void cleanup() {
        sessionRepository.deleteAll();
    }

    @Test
    void post_createsSession_returns201WithMetadata() throws Exception {
        String body = mockMvc.perform(post("/api/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        UUID id = UUID.fromString(json.get("id").asText());
        assertTrue(sessionRepository.findById(id).isPresent());
    }

    @Test
    void get_returnsMetadataForExistingSession() throws Exception {
        Session saved = sessionRepository.save(new Session());

        mockMvc.perform(get("/api/sessions/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.hasPassword").value(false));
    }

    @Test
    void get_returns404ForUnknown() throws Exception {
        mockMvc.perform(get("/api/sessions/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_updatesLockAndPassword_returns200WithMetadata() throws Exception {
        Session saved = sessionRepository.save(new Session());
        String body = objectMapper.writeValueAsString(
                new SessionSettingsDto(null, true, "hunter2"));

        mockMvc.perform(patch("/api/sessions/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.hasPassword").value(true));
    }

    @Test
    void patch_returns404ForUnknown() throws Exception {
        String body = objectMapper.writeValueAsString(
                new SessionSettingsDto(null, true, null));

        mockMvc.perform(patch("/api/sessions/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void putState_storesBlob_returns204() throws Exception {
        Session saved = sessionRepository.save(new Session());
        byte[] blob = {10, 20, 30};

        mockMvc.perform(put("/api/sessions/{id}/state", saved.getId())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(blob))
                .andExpect(status().isNoContent());

        Session reloaded = sessionRepository.findById(saved.getId()).orElseThrow();
        assertArrayEquals(blob, reloaded.getEncryptedState());
    }

    @Test
    void getState_returnsBlob_200() throws Exception {
        Session session = new Session();
        byte[] blob = {1, 2, 3};
        session.setEncryptedState(blob);
        Session saved = sessionRepository.save(session);

        byte[] response = mockMvc.perform(get("/api/sessions/{id}/state", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn().getResponse().getContentAsByteArray();

        assertArrayEquals(blob, response);
    }

    @Test
    void getState_returns204WhenBlobNull() throws Exception {
        Session saved = sessionRepository.save(new Session());

        mockMvc.perform(get("/api/sessions/{id}/state", saved.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void putState_returns403WhenLocked() throws Exception {
        Session session = new Session();
        session.setLocked(true);
        Session saved = sessionRepository.save(session);

        mockMvc.perform(put("/api/sessions/{id}/state", saved.getId())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[]{1}))
                .andExpect(status().isForbidden());
    }

    @Test
    void putState_returns404ForUnknown() throws Exception {
        mockMvc.perform(put("/api/sessions/{id}/state", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[]{1}))
                .andExpect(status().isNotFound());
    }

    @Test
    void getState_returns404ForUnknown() throws Exception {
        mockMvc.perform(get("/api/sessions/{id}/state", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_createResponse_includesSlug() throws Exception {
        mockMvc.perform(post("/api/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").isString())
                .andExpect(jsonPath("$.slug").isNotEmpty());
    }

    @Test
    void get_resolvesBySlug() throws Exception {
        Session session = new Session();
        session.setSlug("share-link-1");
        Session saved = sessionRepository.save(session);

        mockMvc.perform(get("/api/sessions/{id}", "share-link-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.slug").value("share-link-1"));
    }

    @Test
    void getState_resolvesBySlug() throws Exception {
        Session session = new Session();
        session.setSlug("share-link-2");
        byte[] blob = {1, 2, 3};
        session.setEncryptedState(blob);
        sessionRepository.save(session);

        byte[] response = mockMvc.perform(get("/api/sessions/{id}/state", "share-link-2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertArrayEquals(blob, response);
    }

    @Test
    void get_isPublic_whenSessionHasPassword() throws Exception {
        Session session = new Session();
        session.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        Session saved = sessionRepository.save(session);

        mockMvc.perform(get("/api/sessions/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true));
    }

    @Test
    void patch_returns403_whenSessionHasPassword_andNoPasswordHeader() throws Exception {
        Session session = new Session();
        session.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        Session saved = sessionRepository.save(session);

        mockMvc.perform(patch("/api/sessions/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SessionSettingsDto(null, true, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_returns403_whenSessionHasPassword_andWrongPassword() throws Exception {
        Session session = new Session();
        session.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        Session saved = sessionRepository.save(session);

        mockMvc.perform(patch("/api/sessions/{id}", saved.getId())
                        .header("X-Session-Password", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SessionSettingsDto(null, true, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_succeeds_whenSessionHasPassword_andCorrectPassword() throws Exception {
        Session session = new Session();
        session.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        Session saved = sessionRepository.save(session);

        mockMvc.perform(patch("/api/sessions/{id}", saved.getId())
                        .header("X-Session-Password", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SessionSettingsDto(null, true, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));
    }

    @Test
    void getState_returns403_whenSessionHasPassword_andNoPasswordHeader() throws Exception {
        Session session = new Session();
        session.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        Session saved = sessionRepository.save(session);

        mockMvc.perform(get("/api/sessions/{id}/state", saved.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void putState_returns403_whenSessionHasPassword_andNoPasswordHeader() throws Exception {
        Session session = new Session();
        session.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        Session saved = sessionRepository.save(session);

        mockMvc.perform(put("/api/sessions/{id}/state", saved.getId())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[]{1}))
                .andExpect(status().isForbidden());
    }
}

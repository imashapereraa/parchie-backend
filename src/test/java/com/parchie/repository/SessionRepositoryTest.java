package com.parchie.repository;

import com.parchie.model.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SessionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    SessionRepository sessionRepository;

    @AfterEach
    void cleanup() {
        sessionRepository.deleteAll();
    }

    @Test
    void deleteAllExpired_deletesSessionsPastExpiry() {
        Session expired = new Session();
        expired.setExpiresAt(Instant.now().minusSeconds(3600));
        sessionRepository.save(expired);

        int deleted = sessionRepository.deleteAllExpired();

        assertEquals(1, deleted);
        assertTrue(sessionRepository.findAll().isEmpty());
    }

    @Test
    void deleteAllExpired_keepsFutureSessions() {
        Session future = new Session();
        future.setExpiresAt(Instant.now().plusSeconds(3600));
        Session saved = sessionRepository.save(future);

        int deleted = sessionRepository.deleteAllExpired();

        assertEquals(0, deleted);
        assertTrue(sessionRepository.findById(saved.getId()).isPresent());
    }

    @Test
    void deleteAllExpired_mixedSet() {
        Session expiredA = new Session();
        expiredA.setExpiresAt(Instant.now().minusSeconds(3600));
        Session expiredB = new Session();
        expiredB.setExpiresAt(Instant.now().minusSeconds(60));
        Session future = new Session();
        future.setExpiresAt(Instant.now().plusSeconds(3600));

        sessionRepository.save(expiredA);
        sessionRepository.save(expiredB);
        Session savedFuture = sessionRepository.save(future);

        int deleted = sessionRepository.deleteAllExpired();

        assertEquals(2, deleted);
        List<Session> remaining = sessionRepository.findAll();
        assertEquals(1, remaining.size());
        assertEquals(savedFuture.getId(), remaining.get(0).getId());
    }

    @Test
    void deleteAllExpired_emptyTable_returnsZero() {
        int deleted = sessionRepository.deleteAllExpired();

        assertEquals(0, deleted);
    }

    @Test
    void save_andFindById_roundTrip() {
        Session session = new Session();
        Session saved = sessionRepository.save(session);

        UUID id = saved.getId();
        assertNotNull(id);
        assertTrue(sessionRepository.findById(id).isPresent());
    }
}

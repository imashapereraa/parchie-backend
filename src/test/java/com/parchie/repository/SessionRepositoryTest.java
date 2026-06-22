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
    void findAllExpiredBefore_findsSessionsPastExpiry() {
        Session expired = new Session();
        expired.setExpiresAt(Instant.now().minusSeconds(3600));
        sessionRepository.save(expired);

        List<Session> found = sessionRepository.findAllExpiredBefore(Instant.now());

        assertEquals(1, found.size());
        sessionRepository.deleteAll(found);
        assertTrue(sessionRepository.findAll().isEmpty());
    }

    @Test
    void findAllExpiredBefore_keepsFutureSessions() {
        Session future = new Session();
        future.setExpiresAt(Instant.now().plusSeconds(3600));
        Session saved = sessionRepository.save(future);

        List<Session> found = sessionRepository.findAllExpiredBefore(Instant.now());

        assertEquals(0, found.size());
        assertTrue(sessionRepository.findById(saved.getId()).isPresent());
    }

    @Test
    void findAllExpiredBefore_mixedSet() {
        Session expiredA = new Session();
        expiredA.setExpiresAt(Instant.now().minusSeconds(3600));
        Session expiredB = new Session();
        expiredB.setExpiresAt(Instant.now().minusSeconds(60));
        Session future = new Session();
        future.setExpiresAt(Instant.now().plusSeconds(3600));

        sessionRepository.save(expiredA);
        sessionRepository.save(expiredB);
        Session savedFuture = sessionRepository.save(future);

        List<Session> found = sessionRepository.findAllExpiredBefore(Instant.now());
        sessionRepository.deleteAll(found);

        assertEquals(2, found.size());
        List<Session> remaining = sessionRepository.findAll();
        assertEquals(1, remaining.size());
        assertEquals(savedFuture.getId(), remaining.get(0).getId());
    }

    @Test
    void findAllExpiredBefore_emptyTable_returnsEmpty() {
        List<Session> found = sessionRepository.findAllExpiredBefore(Instant.now());

        assertTrue(found.isEmpty());
    }

    @Test
    void save_andFindById_roundTrip() {
        Session session = new Session();
        Session saved = sessionRepository.save(session);

        UUID id = saved.getId();
        assertNotNull(id);
        assertTrue(sessionRepository.findById(id).isPresent());
    }

    @Test
    void findBySlug_returnsSessionWhenSlugMatches() {
        Session session = new Session();
        session.setSlug("abc-defg-hij");
        sessionRepository.save(session);

        assertTrue(sessionRepository.findBySlug("abc-defg-hij").isPresent());
    }

    @Test
    void findBySlug_emptyWhenSlugUnknown() {
        assertTrue(sessionRepository.findBySlug("nope-nope-nop").isEmpty());
    }
}

package com.parchie.model;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
class SessionPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @PersistenceContext
    EntityManager em;

    @Test
    void mutableFields_roundTripThroughDatabase() {
        byte[] blob = {1, 2, 3, 4};
        Session session = new Session();
        session.setLocked(true);
        session.setPasswordHash("$2a$hash");
        session.setEncryptedState(blob);
        em.persist(session);
        em.flush();
        em.clear();

        Session loaded = em.find(Session.class, session.getId());

        assertTrue(loaded.isLocked());
        assertEquals("$2a$hash", loaded.getPasswordHash());
        assertArrayEquals(blob, loaded.getEncryptedState());
    }

    @Test
    void save_preservesExplicitExpiresAt() {
        Instant customExpiry = Instant.now().plusSeconds(3600);
        Session session = new Session();
        session.setExpiresAt(customExpiry);
        em.persist(session);
        em.flush();
        em.clear();

        Session loaded = em.find(Session.class, session.getId());

        assertTrue(Math.abs(ChronoUnit.MILLIS.between(loaded.getExpiresAt(), customExpiry)) < 1000,
                "expiresAt should match the explicitly set value");
    }

    @Test
    void save_defaultsExpiresAtToSevenDays() {
        Session session = new Session();
        em.persist(session);
        em.flush();
        em.clear();

        Session loaded = em.find(Session.class, session.getId());

        long secondsBetween = ChronoUnit.SECONDS.between(loaded.getCreatedAt(), loaded.getExpiresAt());
        assertTrue(secondsBetween >= 7L * 24 * 60 * 60 - 1, "expiresAt should be ~7 days after createdAt");
        assertTrue(secondsBetween <= 7L * 24 * 60 * 60 + 1, "expiresAt should be ~7 days after createdAt");
    }

    @Test
    void save_setsCreatedAt() {
        Instant before = Instant.now();
        Session session = new Session();
        em.persist(session);
        em.flush();
        em.clear();

        Session loaded = em.find(Session.class, session.getId());

        assertNotNull(loaded.getCreatedAt());
        assertFalse(loaded.getCreatedAt().isBefore(before));
        assertFalse(loaded.getCreatedAt().isAfter(Instant.now()));
    }

    @Test
    void save_assignsId_andCanBeRetrieved() {
        Session session = new Session();
        em.persist(session);
        em.flush();
        em.clear();

        Session loaded = em.find(Session.class, session.getId());

        assertNotNull(loaded);
        assertNotNull(loaded.getId());
        assertEquals(session.getId(), loaded.getId());
    }
}

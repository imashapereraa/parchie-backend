package com.parchie.service;

import com.parchie.dto.SessionSettingsDto;
import com.parchie.exception.InvalidPasswordException;
import com.parchie.exception.SessionLockedException;
import com.parchie.exception.SessionNotFoundException;
import com.parchie.model.Session;
import com.parchie.repository.SessionRepository;
import org.mindrot.jbcrypt.BCrypt;
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
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SessionServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    SessionService sessionService;

    @Autowired
    SessionRepository sessionRepository;

    @AfterEach
    void cleanup() {
        sessionRepository.deleteAll();
    }

    @Test
    void createSession_persistsWithDefaultsAndReturnsIt() {
        Session created = sessionService.createSession();

        assertNotNull(created.getId());
        assertNotNull(created.getSlug());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getExpiresAt());
        assertFalse(created.isLocked());
        assertNull(created.getPasswordHash());
        assertNull(created.getEncryptedState());

        // defaults: expires roughly 7 days from now
        Instant sevenDays = Instant.now().plus(7, ChronoUnit.DAYS);
        assertTrue(created.getExpiresAt().isAfter(sevenDays.minusSeconds(60)));
        assertTrue(created.getExpiresAt().isBefore(sevenDays.plusSeconds(60)));

        // persisted, not just constructed
        assertTrue(sessionRepository.findById(created.getId()).isPresent());
    }

    @Test
    void getSession_returnsLiveSession() {
        Session created = sessionService.createSession();

        Optional<Session> found = sessionService.getSession(created.getId());

        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
    }

    @Test
    void getSession_emptyForUnknownId() {
        assertTrue(sessionService.getSession(UUID.randomUUID()).isEmpty());
    }

    @Test
    void getSession_emptyWhenExpired() {
        Session created = sessionService.createSession();
        created.setExpiresAt(Instant.now().minusSeconds(1));
        sessionRepository.save(created);

        assertTrue(sessionService.getSession(created.getId()).isEmpty());
    }

    @Test
    void getSessionOrThrow_returnsLiveSession() {
        Session created = sessionService.createSession();

        Session found = sessionService.getSessionOrThrow(created.getId());

        assertEquals(created.getId(), found.getId());
    }

    @Test
    void getSessionOrThrow_throwsForUnknown() {
        UUID missing = UUID.randomUUID();
        assertThrows(SessionNotFoundException.class, () -> sessionService.getSessionOrThrow(missing));
    }

    @Test
    void getSessionOrThrow_throwsForExpired() {
        Session created = sessionService.createSession();
        created.setExpiresAt(Instant.now().minusSeconds(1));
        sessionRepository.save(created);

        assertThrows(SessionNotFoundException.class, () -> sessionService.getSessionOrThrow(created.getId()));
    }

    @Test
    void getEncryptedState_nullForFreshSession() {
        Session created = sessionService.createSession();

        assertNull(sessionService.getEncryptedState(created.getId()));
    }

    @Test
    void getEncryptedState_returnsStoredBlob() {
        Session created = sessionService.createSession();
        byte[] blob = {1, 2, 3, 4, 5};
        created.setEncryptedState(blob);
        sessionRepository.save(created);

        assertArrayEquals(blob, sessionService.getEncryptedState(created.getId()));
    }

    @Test
    void getEncryptedState_throwsForUnknown() {
        assertThrows(SessionNotFoundException.class, () -> sessionService.getEncryptedState(UUID.randomUUID()));
    }

    @Test
    void updateEncryptedState_persistsBlobAndResetsTtl() {
        Session created = sessionService.createSession();
        // shrink TTL so we can detect the reset
        created.setExpiresAt(Instant.now().plusSeconds(60));
        sessionRepository.save(created);

        byte[] blob = {9, 8, 7};
        sessionService.updateEncryptedState(created.getId(), blob);

        Session reloaded = sessionRepository.findById(created.getId()).orElseThrow();
        assertArrayEquals(blob, reloaded.getEncryptedState());

        Instant sevenDays = Instant.now().plus(7, ChronoUnit.DAYS);
        assertTrue(reloaded.getExpiresAt().isAfter(sevenDays.minusSeconds(60)));
        assertTrue(reloaded.getExpiresAt().isBefore(sevenDays.plusSeconds(60)));
    }

    @Test
    void updateEncryptedState_doesNotShrinkLongTtl() {
        Session created = sessionService.createSession();
        Instant thirtyDays = Instant.now().plus(30, ChronoUnit.DAYS);
        created.setExpiresAt(thirtyDays);
        sessionRepository.save(created);

        sessionService.updateEncryptedState(created.getId(), new byte[]{1});

        Session reloaded = sessionRepository.findById(created.getId()).orElseThrow();
        assertTrue(reloaded.getExpiresAt().isAfter(Instant.now().plus(7, ChronoUnit.DAYS)));
        assertTrue(reloaded.getExpiresAt().isBefore(thirtyDays.plusSeconds(60)));
    }

    @Test
    void updateEncryptedState_throwsWhenLocked() {
        Session created = sessionService.createSession();
        created.setLocked(true);
        sessionRepository.save(created);

        assertThrows(SessionLockedException.class,
                () -> sessionService.updateEncryptedState(created.getId(), new byte[]{1}));
    }

    @Test
    void updateEncryptedState_throwsForUnknown() {
        assertThrows(SessionNotFoundException.class,
                () -> sessionService.updateEncryptedState(UUID.randomUUID(), new byte[]{1}));
    }

    @Test
    void updateSettings_updatesExpiresAtOnly() {
        Session created = sessionService.createSession();
        Instant before = created.getExpiresAt();
        Instant newExpiry = Instant.now().plus(30, ChronoUnit.DAYS);

        Session updated = sessionService.updateSettings(
                created.getId(),
                new SessionSettingsDto(newExpiry, null, null));

        assertEquals(newExpiry, updated.getExpiresAt());
        assertFalse(updated.isLocked());
        assertNull(updated.getPasswordHash());
        assertNotEquals(before, updated.getExpiresAt());
    }

    @Test
    void updateSettings_updatesLockedOnly() {
        Session created = sessionService.createSession();
        Instant originalExpiry = created.getExpiresAt();

        Session updated = sessionService.updateSettings(
                created.getId(),
                new SessionSettingsDto(null, true, null));

        assertTrue(updated.isLocked());
        assertEquals(originalExpiry, updated.getExpiresAt());
    }

    @Test
    void updateSettings_nullPassword_leavesHashUnchanged() {
        Session created = sessionService.createSession();
        created.setPasswordHash("existing-hash");
        sessionRepository.save(created);

        Session updated = sessionService.updateSettings(
                created.getId(),
                new SessionSettingsDto(null, null, null));

        assertEquals("existing-hash", updated.getPasswordHash());
    }

    @Test
    void updateSettings_emptyPassword_clearsHash() {
        Session created = sessionService.createSession();
        created.setPasswordHash("existing-hash");
        sessionRepository.save(created);

        Session updated = sessionService.updateSettings(
                created.getId(),
                new SessionSettingsDto(null, null, ""));

        assertNull(updated.getPasswordHash());
    }

    @Test
    void updateSettings_nonEmptyPassword_storesBcryptHash() {
        Session created = sessionService.createSession();

        Session updated = sessionService.updateSettings(
                created.getId(),
                new SessionSettingsDto(null, null, "hunter2"));

        assertNotNull(updated.getPasswordHash());
        assertNotEquals("hunter2", updated.getPasswordHash());
        assertTrue(BCrypt.checkpw("hunter2", updated.getPasswordHash()));
    }

    @Test
    void updateSettings_allowedEvenWhenLocked() {
        Session created = sessionService.createSession();
        created.setLocked(true);
        sessionRepository.save(created);

        Session updated = sessionService.updateSettings(
                created.getId(),
                new SessionSettingsDto(null, false, null));

        assertFalse(updated.isLocked());
    }

    @Test
    void updateSettings_throwsForUnknown() {
        assertThrows(SessionNotFoundException.class,
                () -> sessionService.updateSettings(UUID.randomUUID(),
                        new SessionSettingsDto(null, true, null)));
    }

    @Test
    void cleanupExpiredSessions_deletesOnlyExpired() {
        Session expired = sessionService.createSession();
        expired.setExpiresAt(Instant.now().minusSeconds(3600));
        sessionRepository.save(expired);

        Session live = sessionService.createSession();

        sessionService.cleanupExpiredSessions();

        assertTrue(sessionRepository.findById(expired.getId()).isEmpty());
        assertTrue(sessionRepository.findById(live.getId()).isPresent());
    }

    @Test
    void assertAccess_succeedsForSessionWithNoPassword() {
        Session created = sessionService.createSession();
        assertDoesNotThrow(() -> sessionService.assertAccess(created.getId(), null));
        assertDoesNotThrow(() -> sessionService.assertAccess(created.getId(), "anything"));
    }

    @Test
    void assertAccess_succeedsWithCorrectPassword() {
        Session created = sessionService.createSession();
        created.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        sessionRepository.save(created);

        assertDoesNotThrow(() -> sessionService.assertAccess(created.getId(), "secret"));
    }

    @Test
    void assertAccess_throwsInvalidPasswordException_whenWrongPassword() {
        Session created = sessionService.createSession();
        created.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        sessionRepository.save(created);

        assertThrows(InvalidPasswordException.class,
                () -> sessionService.assertAccess(created.getId(), "wrong"));
    }

    @Test
    void assertAccess_throwsInvalidPasswordException_whenNoPasswordProvided() {
        Session created = sessionService.createSession();
        created.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        sessionRepository.save(created);

        assertThrows(InvalidPasswordException.class,
                () -> sessionService.assertAccess(created.getId(), null));
    }

    @Test
    void assertAccess_throwsSessionNotFoundException_forUnknownSession() {
        assertThrows(SessionNotFoundException.class,
                () -> sessionService.assertAccess(UUID.randomUUID(), null));
    }

    @Test
    void createSession_assignsUniqueSlug() {
        Session a = sessionService.createSession();
        Session b = sessionService.createSession();

        assertNotNull(a.getSlug());
        assertNotNull(b.getSlug());
        assertNotEquals(a.getSlug(), b.getSlug());
    }

    @Test
    void resolveOrThrow_findsSessionByUuidString() {
        Session created = sessionService.createSession();

        Session resolved = sessionService.resolveOrThrow(created.getId().toString());

        assertEquals(created.getId(), resolved.getId());
    }

    @Test
    void resolveOrThrow_findsSessionBySlug() {
        Session created = sessionService.createSession();

        Session resolved = sessionService.resolveOrThrow(created.getSlug());

        assertEquals(created.getId(), resolved.getId());
        assertEquals(created.getSlug(), resolved.getSlug());
    }

    @Test
    void resolveOrThrow_throwsForUnknownSlug() {
        assertThrows(SessionNotFoundException.class,
                () -> sessionService.resolveOrThrow("no-such-slug"));
    }

    @Test
    void resolveOrThrow_throwsForUnknownUuid() {
        assertThrows(SessionNotFoundException.class,
                () -> sessionService.resolveOrThrow(UUID.randomUUID().toString()));
    }

    @Test
    void resolveOrThrow_throwsForExpiredSession() {
        Session created = sessionService.createSession();
        created.setExpiresAt(Instant.now().minusSeconds(1));
        sessionRepository.save(created);

        assertThrows(SessionNotFoundException.class,
                () -> sessionService.resolveOrThrow(created.getSlug()));
    }

    @Test
    void resolveOrThrow_throwsForBlankIdentifier() {
        assertThrows(SessionNotFoundException.class, () -> sessionService.resolveOrThrow(""));
        assertThrows(SessionNotFoundException.class, () -> sessionService.resolveOrThrow(null));
    }
}

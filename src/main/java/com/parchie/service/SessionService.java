package com.parchie.service;

import com.parchie.dto.SessionSettingsDto;
import com.parchie.exception.InvalidPasswordException;
import com.parchie.exception.SessionLockedException;
import com.parchie.exception.SessionNotFoundException;
import com.parchie.model.Session;
import com.parchie.repository.SessionRepository;
import com.parchie.websocket.SessionRelayHandler;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.CloseStatus;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    // avoid visually ambiguous chars (l, o, 0, 1).
    private static final String SLUG_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final int[] SLUG_GROUP_SIZES = {3, 4, 3};
    private static final int SLUG_MAX_ATTEMPTS = 5;

    private final SessionRepository sessionRepository;
    private final SessionRelayHandler relayHandler;
    private final SecureRandom random = new SecureRandom();

    public SessionService(SessionRepository sessionRepository, SessionRelayHandler relayHandler) {
        this.sessionRepository = sessionRepository;
        this.relayHandler = relayHandler;
    }

    @Transactional
    public Session createSession() {
        Session session = new Session();
        session.setSlug(generateUniqueSlug());
        return sessionRepository.save(session);
    }

         // create a session that belongs to a user. The row is marked as
     // permanent by pushing `expires_at` to a far-future sentinel — the
     // scheduled expiry sweep treats it as never-expiring.
    @Transactional
    public Session createOwnedSession(UUID ownerId) {
        Session session = new Session();
        session.setSlug(generateUniqueSlug());
        session.setOwnerId(ownerId);
        session.setExpiresAt(Instant.parse("9999-12-31T23:59:59Z"));
        return sessionRepository.save(session);
    }

    public Optional<Session> getSession(UUID id) {
        return sessionRepository.findById(id).filter(s -> !s.isExpired());
    }

    public Session getSessionOrThrow(UUID id) {
        return getSession(id).orElseThrow(() -> new SessionNotFoundException(id));
    }

    public Session resolveOrThrow(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.isBlank()) {
            throw new SessionNotFoundException(String.valueOf(idOrSlug));
        }
        UUID parsedId = tryParseUuid(idOrSlug);
        Optional<Session> session = (parsedId != null)
                ? sessionRepository.findById(parsedId)
                : sessionRepository.findBySlug(idOrSlug);
        return session
                .filter(s -> !s.isExpired())
                .orElseThrow(() -> new SessionNotFoundException(idOrSlug));
    }

    public void assertAccess(UUID id, String password) {
        checkPassword(getSessionOrThrow(id), password);
    }

    public void assertAccess(String idOrSlug, String password) {
        checkPassword(resolveOrThrow(idOrSlug), password);
    }

    public byte[] getEncryptedState(UUID id) {
        return getSessionOrThrow(id).getEncryptedState();
    }

    public byte[] getEncryptedState(String idOrSlug) {
        return resolveOrThrow(idOrSlug).getEncryptedState();
    }

    @Transactional
    public void updateEncryptedState(UUID id, byte[] blob) {
        applyEncryptedState(getSessionOrThrow(id), blob);
    }

    @Transactional
    public void updateEncryptedState(String idOrSlug, byte[] blob) {
        applyEncryptedState(resolveOrThrow(idOrSlug), blob);
    }

    @Transactional
    public Session updateSettings(UUID id, SessionSettingsDto dto) {
        return applySettings(getSessionOrThrow(id), dto);
    }

    @Transactional
    public Session updateSettings(String idOrSlug, SessionSettingsDto dto) {
        return applySettings(resolveOrThrow(idOrSlug), dto);
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void cleanupExpiredSessions() {
        List<Session> expired = sessionRepository.findAllExpiredBefore(Instant.now());
        if (expired.isEmpty()) return;
        sessionRepository.deleteAll(expired);
        for (Session s : expired) {
            relayHandler.closeRoom(s.getId().toString(), CloseStatus.GOING_AWAY.withReason("session expired"));
        }
        log.info("Cleaned up {} expired sessions", expired.size());
    }

    private void checkPassword(Session session, String password) {
        if (!session.passwordMatches(password)) {
            throw new InvalidPasswordException();
        }
    }

    private void applyEncryptedState(Session session, byte[] blob) {
        if (session.isLocked()) {
            throw new SessionLockedException(session.getId());
        }
        session.setEncryptedState(blob);
        Instant sevenDaysFromNow = Instant.now().plus(7, ChronoUnit.DAYS);
        if (session.getExpiresAt().isBefore(sevenDaysFromNow)) {
            session.setExpiresAt(sevenDaysFromNow);
        }
        sessionRepository.save(session);
    }

    private Session applySettings(Session session, SessionSettingsDto dto) {
        if (dto.expiresAt() != null) {
            session.setExpiresAt(dto.expiresAt());
        }
        if (dto.locked() != null) {
            session.setLocked(dto.locked());
        }
        if (dto.password() != null) {
            if (dto.password().isEmpty()) {
                session.setPasswordHash(null);
            } else {
                session.setPasswordHash(BCrypt.hashpw(dto.password(), BCrypt.gensalt()));
            }
        }
        return sessionRepository.save(session);
    }

    private UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String generateUniqueSlug() {
        for (int attempt = 0; attempt < SLUG_MAX_ATTEMPTS; attempt++) {
            String slug = generateSlug();
            if (sessionRepository.findBySlug(slug).isEmpty()) {
                return slug;
            }
        }
        throw new IllegalStateException("Failed to generate a unique slug after " + SLUG_MAX_ATTEMPTS + " attempts");
    }

    private String generateSlug() {
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < SLUG_GROUP_SIZES.length; g++) {
            if (g > 0) sb.append('-');
            for (int i = 0; i < SLUG_GROUP_SIZES[g]; i++) {
                sb.append(SLUG_ALPHABET.charAt(random.nextInt(SLUG_ALPHABET.length())));
            }
        }
        return sb.toString();
    }
}

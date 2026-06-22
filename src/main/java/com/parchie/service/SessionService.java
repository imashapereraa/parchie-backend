package com.parchie.service;

import com.parchie.dto.SessionSettingsDto;
import com.parchie.exception.InvalidPasswordException;
import com.parchie.exception.SessionLockedException;
import com.parchie.exception.SessionNotFoundException;
import com.parchie.model.Session;
import com.parchie.repository.SessionRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public Session createSession() {
        return sessionRepository.save(new Session());
    }

    public Optional<Session> getSession(UUID id) {
        return sessionRepository.findById(id).filter(s -> !s.isExpired());
    }

    public Session getSessionOrThrow(UUID id) {
        return getSession(id).orElseThrow(() -> new SessionNotFoundException(id));
    }

    public void assertAccess(UUID id, String password) {
        Session session = getSessionOrThrow(id);
        if (!session.passwordMatches(password)) {
            throw new InvalidPasswordException();
        }
    }

    public byte[] getEncryptedState(UUID id) {
        return getSessionOrThrow(id).getEncryptedState();
    }

    @Transactional
    public void updateEncryptedState(UUID id, byte[] blob) {
        Session session = getSessionOrThrow(id);
        if (session.isLocked()) {
            throw new SessionLockedException(id);
        }
        session.setEncryptedState(blob);
        Instant sevenDaysFromNow = Instant.now().plus(7, ChronoUnit.DAYS);
        if (session.getExpiresAt().isBefore(sevenDaysFromNow)) {
            session.setExpiresAt(sevenDaysFromNow);
        }
        sessionRepository.save(session);
    }

    @Transactional
    public Session updateSettings(UUID id, SessionSettingsDto dto) {
        Session session = getSessionOrThrow(id);
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

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void cleanupExpiredSessions() {
        int deleted = sessionRepository.deleteAllExpired();
        log.info("Cleaned up {} expired sessions", deleted);
    }
}

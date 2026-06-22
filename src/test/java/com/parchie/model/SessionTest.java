package com.parchie.model;

import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SessionTest {

    @Test
    void isExpired_true_whenExpiresAtIsInPast() {
        Session session = new Session();
        session.setExpiresAt(Instant.now().minusSeconds(1));
        assertTrue(session.isExpired());
    }

    @Test
    void isExpired_false_whenExpiresAtIsInFuture() {
        Session session = new Session();
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        assertFalse(session.isExpired());
    }

    @Test
    void defaults_notLocked_noPassword_noState() {
        Session session = new Session();
        assertFalse(session.isLocked());
        assertNull(session.getPasswordHash());
        assertNull(session.getEncryptedState());
    }

    @Test
    void passwordMatches_trueWhenNoPassword() {
        Session session = new Session();
        assertTrue(session.passwordMatches(null));
        assertTrue(session.passwordMatches("anything"));
    }

    @Test
    void passwordMatches_falseWhenHasPasswordAndNullOrBlankPlaintext() {
        Session session = new Session();
        session.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        assertFalse(session.passwordMatches(null));
        assertFalse(session.passwordMatches(""));
        assertFalse(session.passwordMatches("   "));
    }

    @Test
    void passwordMatches_falseWhenWrongPlaintext() {
        Session session = new Session();
        session.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        assertFalse(session.passwordMatches("wrong"));
    }

    @Test
    void passwordMatches_trueWhenCorrectPlaintext() {
        Session session = new Session();
        session.setPasswordHash(BCrypt.hashpw("secret", BCrypt.gensalt()));
        assertTrue(session.passwordMatches("secret"));
    }
}

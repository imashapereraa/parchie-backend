package com.parchie.dto;

import com.parchie.model.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionMetadataDtoTest {

    @Test
    void from_copiesEntityFields() throws Exception {
        Session session = newSessionWithId(UUID.randomUUID());
        setField(session, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        session.setExpiresAt(Instant.parse("2026-01-08T00:00:00Z"));
        session.setLocked(true);
        session.setSlug("r7h-rxkt-fix");

        SessionMetadataDto dto = SessionMetadataDto.from(session);

        assertEquals(session.getId(), dto.id());
        assertEquals("r7h-rxkt-fix", dto.slug());
        assertEquals(session.getCreatedAt(), dto.createdAt());
        assertEquals(session.getExpiresAt(), dto.expiresAt());
        assertTrue(dto.locked());
    }

    @Test
    void from_hasPassword_falseWhenHashNull() {
        Session session = new Session();
        session.setPasswordHash(null);

        SessionMetadataDto dto = SessionMetadataDto.from(session);

        assertFalse(dto.hasPassword());
    }

    @Test
    void from_hasPassword_trueWhenHashPresent() {
        Session session = new Session();
        session.setPasswordHash("$2a$10$abcdef");

        SessionMetadataDto dto = SessionMetadataDto.from(session);

        assertTrue(dto.hasPassword());
    }

    @Test
    void dto_doesNotExposePasswordHash() {
        for (Field f : SessionMetadataDto.class.getDeclaredFields()) {
            assertNotEquals("passwordHash", f.getName(),
                    "SessionMetadataDto must never expose the raw password hash");
        }
    }

    private static Session newSessionWithId(UUID id) throws Exception {
        Session s = new Session();
        setField(s, "id", id);
        return s;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = Session.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}

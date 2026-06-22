package com.parchie.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SessionSettingsDtoTest {

    @Test
    void allFieldsNullable_meansPartialUpdate() {
        SessionSettingsDto dto = new SessionSettingsDto(null, null, null);
        assertNull(dto.expiresAt());
        assertNull(dto.locked());
        assertNull(dto.password());
    }

    @Test
    void fieldsExposed_inExpectedTypes() {
        Instant expiry = Instant.parse("2026-07-01T00:00:00Z");
        SessionSettingsDto dto = new SessionSettingsDto(expiry, true, "hunter2");
        assertEquals(expiry, dto.expiresAt());
        assertEquals(Boolean.TRUE, dto.locked());
        assertEquals("hunter2", dto.password());
    }
}

package com.parchie.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionLockedExceptionTest {

    @Test
    void message_includesSessionId() {
        UUID id = UUID.randomUUID();
        SessionLockedException ex = new SessionLockedException(id);
        assertTrue(ex.getMessage().contains(id.toString()),
                "exception message should contain the session id");
    }

    @Test
    void isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new SessionLockedException(UUID.randomUUID()));
    }

    @Test
    void hasResponseStatus_forbidden() {
        ResponseStatus status = SessionLockedException.class.getAnnotation(ResponseStatus.class);
        assertNotNull(status, "class must be annotated with @ResponseStatus");
        assertEquals(HttpStatus.FORBIDDEN, status.value());
    }
}

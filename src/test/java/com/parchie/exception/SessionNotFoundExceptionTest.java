package com.parchie.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionNotFoundExceptionTest {

    @Test
    void message_includesSessionId() {
        UUID id = UUID.randomUUID();
        SessionNotFoundException ex = new SessionNotFoundException(id);
        assertTrue(ex.getMessage().contains(id.toString()),
                "exception message should contain the session id");
    }

    @Test
    void isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new SessionNotFoundException(UUID.randomUUID()));
    }

    @Test
    void hasResponseStatus_notFound() {
        ResponseStatus status = SessionNotFoundException.class.getAnnotation(ResponseStatus.class);
        assertNotNull(status, "class must be annotated with @ResponseStatus");
        assertEquals(HttpStatus.NOT_FOUND, status.value());
    }
}

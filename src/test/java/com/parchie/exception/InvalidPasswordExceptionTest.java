package com.parchie.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.junit.jupiter.api.Assertions.*;

class InvalidPasswordExceptionTest {

    @Test
    void isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new InvalidPasswordException());
    }

    @Test
    void hasResponseStatus_forbidden() {
        ResponseStatus status = InvalidPasswordException.class.getAnnotation(ResponseStatus.class);
        assertNotNull(status, "class must be annotated with @ResponseStatus");
        assertEquals(HttpStatus.FORBIDDEN, status.value());
    }
}

package com.parchie.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class SessionLockedException extends RuntimeException {

    public SessionLockedException(UUID id) {
        super("Session is locked: " + id);
    }
}

package com.parchie.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTreeMoveException extends RuntimeException {

    public InvalidTreeMoveException(String message) {
        super(message);
    }
}

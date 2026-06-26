package com.parchie.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DocumentNameConflictException extends RuntimeException {

    public DocumentNameConflictException(String name) {
        super("A file or folder already exists with that name: " + name);
    }
}

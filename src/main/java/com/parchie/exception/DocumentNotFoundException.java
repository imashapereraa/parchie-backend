package com.parchie.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID id) {
        // Generic message — used for both "doesn't exist" and "not yours" so
        // we don't leak whether a document with that id belongs to someone.
        super("Document not found: " + id);
    }
}

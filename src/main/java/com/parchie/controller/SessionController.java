package com.parchie.controller;

import com.parchie.dto.SessionMetadataDto;
import com.parchie.dto.SessionSettingsDto;
import com.parchie.model.Session;
import com.parchie.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionMetadataDto create() {
        Session created = sessionService.createSession();
        return SessionMetadataDto.from(created);
    }

    @GetMapping("/{id}")
    public SessionMetadataDto get(@PathVariable UUID id) {
        return SessionMetadataDto.from(sessionService.getSessionOrThrow(id));
    }

    @PatchMapping("/{id}")
    public SessionMetadataDto updateSettings(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Session-Password", required = false) String password,
            @RequestBody SessionSettingsDto dto) {
        sessionService.assertAccess(id, password);
        return SessionMetadataDto.from(sessionService.updateSettings(id, dto));
    }

    @GetMapping(value = "/{id}/state", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getState(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Session-Password", required = false) String password) {
        sessionService.assertAccess(id, password);
        byte[] blob = sessionService.getEncryptedState(id);
        if (blob == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(blob);
    }

    @PutMapping(value = "/{id}/state", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void putState(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Session-Password", required = false) String password,
            @RequestBody byte[] blob) {
        sessionService.assertAccess(id, password);
        sessionService.updateEncryptedState(id, blob);
    }
}

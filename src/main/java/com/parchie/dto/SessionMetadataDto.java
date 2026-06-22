package com.parchie.dto;

import com.parchie.model.Session;

import java.time.Instant;
import java.util.UUID;

public record SessionMetadataDto(
        UUID id,
        Instant createdAt,
        Instant expiresAt,
        boolean locked,
        boolean hasPassword
) {

    public static SessionMetadataDto from(Session session) {
        return new SessionMetadataDto(
                session.getId(),
                session.getCreatedAt(),
                session.getExpiresAt(),
                session.isLocked(),
                session.getPasswordHash() != null
        );
    }
}

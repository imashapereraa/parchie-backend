package com.parchie.dto;

import com.parchie.model.Session;

import java.time.Instant;
import java.util.UUID;

public record SessionMetadataDto(
        UUID id,
        String slug,
        Instant createdAt,
        Instant expiresAt,
        boolean locked,
        boolean hasPassword
) {

    public static SessionMetadataDto from(Session session) {
        return new SessionMetadataDto(
                session.getId(),
                session.getSlug(),
                session.getCreatedAt(),
                session.getExpiresAt(),
                session.isLocked(),
                session.getPasswordHash() != null
        );
    }
}

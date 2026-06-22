package com.parchie.dto;

import java.time.Instant;

public record SessionSettingsDto(
        Instant expiresAt,
        Boolean locked,
        String password
) {
}

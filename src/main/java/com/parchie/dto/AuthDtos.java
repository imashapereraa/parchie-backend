package com.parchie.dto;

import com.parchie.model.User;

import java.time.Instant;

// request/response DTOs for the auth endpoints. Grouped in one file because
// each shape is a small record without behaviour — splitting them gives no
// benefit and adds noise.
public final class AuthDtos {

    private AuthDtos() {}

    public record Credentials(String username, String password) {}

    public record TokenResponse(
            String token,
            Instant expiresAt,
            UserResponse user) {

        public static TokenResponse of(String token, Instant expiresAt, User u) {
            return new TokenResponse(token, expiresAt, UserResponse.from(u));
        }
    }

    public record UserResponse(String id, String username) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId().toString(), u.getUsername());
        }
    }
}

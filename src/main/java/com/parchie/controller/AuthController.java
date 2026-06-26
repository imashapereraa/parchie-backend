package com.parchie.controller;

import com.parchie.dto.AuthDtos.Credentials;
import com.parchie.dto.AuthDtos.TokenResponse;
import com.parchie.dto.AuthDtos.UserResponse;
import com.parchie.model.User;
import com.parchie.service.AuthService;
import com.parchie.service.AuthService.Issued;
import com.parchie.web.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@RequestBody Credentials body) {
        Issued issued = authService.register(body.username(), body.password());
        return TokenResponse.of(
                issued.token().getToken(),
                issued.token().getExpiresAt(),
                issued.user());
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody Credentials body) {
        Issued issued = authService.login(body.username(), body.password());
        return TokenResponse.of(
                issued.token().getToken(),
                issued.token().getExpiresAt(),
                issued.user());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header != null && header.startsWith(BEARER)) {
            authService.logout(header.substring(BEARER.length()).trim());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticatedUser(required = false) User user) {
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(UserResponse.from(user));
    }
}

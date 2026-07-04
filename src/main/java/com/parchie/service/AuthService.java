package com.parchie.service;

import com.parchie.exception.InvalidCredentialsException;
import com.parchie.exception.UsernameTakenException;
import com.parchie.model.AuthToken;
import com.parchie.model.User;
import com.parchie.repository.AuthTokenRepository;
import com.parchie.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MIN_USERNAME = 3;
    private static final int MAX_USERNAME = 64;
    private static final int MIN_PASSWORD = 8;
    private static final long TOKEN_TTL_DAYS = 30;
    private static final int BCRYPT_COST = 12;

    private final UserRepository userRepository;
    private final AuthTokenRepository tokenRepository;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository, AuthTokenRepository tokenRepository) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public Issued register(String username, String password) {
        validateCredentials(username, password);
        // cheap pre-check; the unique-lower index is the real guard against
        // a race between two concurrent registrations of the same name.
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw new UsernameTakenException(username);
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_COST)));
        try {
            user = userRepository.save(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // unique index caught a race; surface the same business error.
            throw new UsernameTakenException(username);
        }
        return new Issued(user, issueToken(user));
    }

    @Transactional
    public Issued login(String username, String password) {
        Optional<User> found = userRepository.findByUsernameIgnoreCase(username);
        if (found.isEmpty() || !found.get().passwordMatches(password)) {
            // intentionally identical error for "no such user" and "wrong
            // password" so we don't leak which usernames exist.
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        return new Issued(user, issueToken(user));
    }

    @Transactional
    public void logout(String token) {
        tokenRepository.deleteById(token);
    }

    // returns the user if the token is valid AND not expired, else empty.
    public Optional<User> resolve(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Optional<AuthToken> row = tokenRepository.findById(token);
        if (row.isEmpty() || row.get().isExpired()) return Optional.empty();
        return userRepository.findById(row.get().getUserId());
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    public void sweepExpiredTokens() {
        int n = tokenRepository.deleteAllExpired(Instant.now());
        if (n > 0) log.info("Cleaned {} expired auth token(s)", n);
    }

    private void validateCredentials(String username, String password) {
        if (username == null || username.length() < MIN_USERNAME || username.length() > MAX_USERNAME) {
            throw new InvalidCredentialsException(
                    "Username must be %d-%d chars".formatted(MIN_USERNAME, MAX_USERNAME));
        }
        if (!username.matches("[A-Za-z0-9_-]+")) {
            throw new InvalidCredentialsException("Username may only contain letters, digits, _ and -");
        }
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new InvalidCredentialsException(
                    "Password must be at least %d chars".formatted(MIN_PASSWORD));
        }
    }

    private AuthToken issueToken(User user) {
        AuthToken t = new AuthToken();
        t.setToken(randomToken());
        t.setUserId(user.getId());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        t.setCreatedAt(now);
        t.setExpiresAt(now.plus(TOKEN_TTL_DAYS, ChronoUnit.DAYS));
        return tokenRepository.save(t);
    }

    private String randomToken() {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        // URL-safe, no padding — 43 chars from 32 random bytes, well under
        // the column's VARCHAR(64).
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public record Issued(User user, AuthToken token) {}
}

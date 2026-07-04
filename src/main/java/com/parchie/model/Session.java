package com.parchie.model;

import jakarta.persistence.*;
import org.mindrot.jbcrypt.BCrypt;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;

    @Column(name = "password_hash", length = 72)
    private String passwordHash;

    @Column(name = "slug", length = 32, unique = true)
    private String slug;

    @Column(name = "encrypted_state", columnDefinition = "bytea")
    private byte[] encryptedState;

    // null for anonymous rooms (existing /api/sessions flow). Non-null when a
     // document in a user's tree backs this session — those sessions never
     // expire, see {@code expires_at} set to a far-future sentinel. */
    @Column(name = "owner_id")
    private UUID ownerId;

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (expiresAt == null) {
            expiresAt = createdAt.plusSeconds(7L * 24 * 60 * 60);
        }
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean passwordMatches(String plaintext) {
        if (passwordHash == null) return true;
        if (plaintext == null || plaintext.isBlank()) return false;
        return BCrypt.checkpw(plaintext, passwordHash);
    }

    public UUID getId() { return id; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getExpiresAt() { return expiresAt; }

    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isLocked() { return locked; }

    public void setLocked(boolean locked) { this.locked = locked; }

    public String getPasswordHash() { return passwordHash; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public byte[] getEncryptedState() { return encryptedState; }

    public void setEncryptedState(byte[] encryptedState) { this.encryptedState = encryptedState; }

    public String getSlug() { return slug; }

    public void setSlug(String slug) { this.slug = slug; }

    public UUID getOwnerId() { return ownerId; }

    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
}
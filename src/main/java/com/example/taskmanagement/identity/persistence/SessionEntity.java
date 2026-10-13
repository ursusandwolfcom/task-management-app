package com.example.taskmanagement.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_sessions", schema = "task_management")
public class SessionEntity {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private long userId;
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private boolean revoked;
    @Version
    private long version;

    protected SessionEntity() {
    }

    public SessionEntity(UUID id, long userId, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID id() {
        return id;
    }

    public long userId() {
        return userId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean accepts(String hash, Instant now) {
        return active(now) && tokenHash.equals(hash);
    }

    public boolean active(Instant now) {
        return !revoked && now.isBefore(expiresAt);
    }

    public void rotate(String hash) {
        tokenHash = hash;
    }

    public void revoke() {
        revoked = true;
    }
}

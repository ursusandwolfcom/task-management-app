package com.example.taskmanagement.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "telegram_link_tokens", schema = "task_management")
public class TelegramLinkTokenEntity {
    @Id
    @Column(length = 64)
    private String token;
    @Column(name = "user_id", nullable = false)
    private long userId;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;

    protected TelegramLinkTokenEntity() {
    }

    public TelegramLinkTokenEntity(String token, long userId, Instant expiresAt) {
        this.token = token;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public String token() {
        return token;
    }

    public long userId() {
        return userId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant usedAt() {
        return usedAt;
    }

    public void markUsed(Instant at) {
        this.usedAt = at;
    }
}

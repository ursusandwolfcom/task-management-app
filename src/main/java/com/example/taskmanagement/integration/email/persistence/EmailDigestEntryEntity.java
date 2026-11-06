package com.example.taskmanagement.integration.email.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "email_digest_entries", schema = "task_management")
public class EmailDigestEntryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;
    @Column(name = "subject_line", nullable = false, length = 200, updatable = false)
    private String subjectLine;
    @Column(name = "body_line", nullable = false, length = 2000, updatable = false)
    private String bodyLine;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "outbox_message_id", updatable = false)
    private Long outboxMessageId;

    protected EmailDigestEntryEntity() {
    }

    public EmailDigestEntryEntity(long userId, String subjectLine, String bodyLine, Instant createdAt) {
        this(userId, subjectLine, bodyLine, createdAt, null);
    }

    public EmailDigestEntryEntity(long userId, String subjectLine, String bodyLine, Instant createdAt,
            Long outboxMessageId) {
        this.userId = userId;
        this.subjectLine = subjectLine;
        this.bodyLine = bodyLine;
        this.createdAt = createdAt;
        this.outboxMessageId = outboxMessageId;
    }

    public Long outboxMessageId() {
        return outboxMessageId;
    }

    public long userId() {
        return userId;
    }

    public String subjectLine() {
        return subjectLine;
    }

    public String bodyLine() {
        return bodyLine;
    }
}

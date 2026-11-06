package com.example.taskmanagement.integration.outbox.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import com.example.taskmanagement.integration.outbox.api.OutboxRetryOperations.Channel;
import com.example.taskmanagement.integration.outbox.domain.DeliveryRetryPolicy;

@Entity
@Table(name = "outbox_messages", schema = "task_management")
public class OutboxMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    private String eventType;
    @Column(nullable = false, length = 4000, updatable = false)
    private String payload;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "processed_at")
    private Instant processedAt;
    @Column(name = "telegram_processed_at")
    private Instant telegramProcessedAt;
    @Column(name = "email_processed_at")
    private Instant emailProcessedAt;
    @Column(name = "in_app_failures", nullable = false)
    private int inAppFailures;
    @Column(name = "email_failures", nullable = false)
    private int emailFailures;
    @Column(name = "telegram_failures", nullable = false)
    private int telegramFailures;
    @Column(name = "in_app_next_attempt_at")
    private Instant inAppNextAttemptAt;
    @Column(name = "email_next_attempt_at")
    private Instant emailNextAttemptAt;
    @Column(name = "telegram_next_attempt_at")
    private Instant telegramNextAttemptAt;

    protected OutboxMessageEntity() {
    }

    public OutboxMessageEntity(String eventType, String payload, Instant createdAt) {
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long id() {
        return id;
    }

    public String eventType() {
        return eventType;
    }

    public String payload() {
        return payload;
    }

    public void markProcessed(Instant at) {
        this.processedAt = at;
    }

    public void markTelegramProcessed(Instant at) {
        this.telegramProcessedAt = at;
    }

    public void markEmailProcessed(Instant at) {
        this.emailProcessedAt = at;
    }

    public void defer(Channel channel, Instant now, DeliveryRetryPolicy policy) {
        switch (channel) {
            case IN_APP -> {
                if (processedAt == null) {
                    inAppFailures = increment(inAppFailures);
                    inAppNextAttemptAt = policy.nextAttempt(now, inAppFailures);
                }
            }
            case EMAIL -> {
                if (emailProcessedAt == null) {
                    emailFailures = increment(emailFailures);
                    emailNextAttemptAt = policy.nextAttempt(now, emailFailures);
                }
            }
            case TELEGRAM -> {
                if (telegramProcessedAt == null) {
                    telegramFailures = increment(telegramFailures);
                    telegramNextAttemptAt = policy.nextAttempt(now, telegramFailures);
                }
            }
        }
    }

    private static int increment(int count) {
        return count == Integer.MAX_VALUE ? count : count + 1;
    }
}

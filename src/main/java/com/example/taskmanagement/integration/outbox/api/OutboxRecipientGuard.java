package com.example.taskmanagement.integration.outbox.api;

/** Current eligibility check for recipient-specific events, including deferred digest sends. */
public interface OutboxRecipientGuard {
    boolean permits(OutboxMessage message, long recipientId);
}

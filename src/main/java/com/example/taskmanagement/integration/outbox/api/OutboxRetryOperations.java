package com.example.taskmanagement.integration.outbox.api;

/** Durable failure timing. Dispatchers call this after a failed attempt has rolled back. */
public interface OutboxRetryOperations {
    enum Channel {
        IN_APP, EMAIL, TELEGRAM
    }

    void defer(long messageId, Channel channel);
}

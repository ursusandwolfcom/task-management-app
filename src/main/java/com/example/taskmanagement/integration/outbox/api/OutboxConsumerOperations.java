package com.example.taskmanagement.integration.outbox.api;

import java.util.List;

/** Consumers claim unprocessed rows, apply side effects, then mark processed per channel. */
public interface OutboxConsumerOperations {
    List<OutboxMessage> claimUnprocessed(int limit);

    void markProcessed(long id);

    List<OutboxMessage> claimTelegramUnprocessed(int limit);

    void markTelegramProcessed(long id);

    List<OutboxMessage> claimEmailUnprocessed(int limit);

    void markEmailProcessed(long id);
}

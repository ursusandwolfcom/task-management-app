package com.example.taskmanagement.integration.outbox.api;

import java.util.List;
import java.util.Optional;

/**
 * Consumers must claim, apply side effects and mark processed within one caller transaction.
 * Claims lock rows until that transaction ends, including across different channels.
 * Provider acceptance followed by rollback can still cause retries and duplicate external delivery.
 */
public interface OutboxConsumerOperations {
    Optional<OutboxMessage> findMessage(long id);

    List<OutboxMessage> claimUnprocessed(int limit);

    void markProcessed(long id);

    List<OutboxMessage> claimTelegramUnprocessed(int limit);

    void markTelegramProcessed(long id);

    List<OutboxMessage> claimEmailUnprocessed(int limit);

    void markEmailProcessed(long id);
}

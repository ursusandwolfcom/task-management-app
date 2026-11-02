package com.example.taskmanagement.integration.outbox.api;

import java.util.List;

/** Consumers claim unprocessed rows, apply side effects, then mark processed. */
public interface OutboxConsumerOperations {
    List<OutboxMessage> claimUnprocessed(int limit);

    void markProcessed(long id);
}

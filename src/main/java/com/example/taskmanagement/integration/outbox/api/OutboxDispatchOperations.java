package com.example.taskmanagement.integration.outbox.api;

import com.example.taskmanagement.integration.outbox.api.OutboxRetryOperations.Channel;
import java.util.function.Consumer;

/** Bounded dispatch with one transaction per event, followed by durable backoff on failure. */
public interface OutboxDispatchOperations {
    int dispatch(Channel channel, int limit, Consumer<OutboxMessage> delivery);
}

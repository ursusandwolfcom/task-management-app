package com.example.taskmanagement.integration.outbox.application;

import com.example.taskmanagement.integration.outbox.api.OutboxConsumerOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxDispatchOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxMessage;
import com.example.taskmanagement.integration.outbox.api.OutboxRetryOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxRetryOperations.Channel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxDispatcher implements OutboxDispatchOperations {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final OutboxConsumerOperations events;
    private final OutboxRetryOperations retries;
    private final TransactionTemplate transaction;

    public OutboxDispatcher(OutboxConsumerOperations events, OutboxRetryOperations retries,
            PlatformTransactionManager transactions) {
        this.events = events;
        this.retries = retries;
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public int dispatch(Channel channel, int limit, Consumer<OutboxMessage> delivery) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(delivery, "delivery");
        int completed = 0;
        for (int attempt = 0; attempt < Math.clamp(limit, 1, 100); attempt++) {
            var selected = new AtomicReference<OutboxMessage>();
            try {
                boolean found = Boolean.TRUE.equals(transaction.execute(status -> {
                    var pending = claim(channel);
                    if (pending.isEmpty()) {
                        return false;
                    }
                    var message = pending.getFirst();
                    selected.set(message);
                    delivery.accept(message);
                    acknowledge(channel, message.id());
                    return true;
                }));
                if (!found) {
                    break;
                }
                completed++;
            } catch (RuntimeException exception) {
                var message = selected.get();
                if (message == null) {
                    throw exception;
                }
                // Failed work has rolled back and released its row lock before recording the failure.
                transaction.executeWithoutResult(status -> retries.defer(message.id(), channel));
                LOG.warn("Outbox delivery deferred for channel {} and event {}", channel, message.id());
            }
        }
        return completed;
    }

    private List<OutboxMessage> claim(Channel channel) {
        return switch (channel) {
            case IN_APP -> events.claimUnprocessed(1);
            case EMAIL -> events.claimEmailUnprocessed(1);
            case TELEGRAM -> events.claimTelegramUnprocessed(1);
        };
    }

    private void acknowledge(Channel channel, long id) {
        switch (channel) {
            case IN_APP -> events.markProcessed(id);
            case EMAIL -> events.markEmailProcessed(id);
            case TELEGRAM -> events.markTelegramProcessed(id);
        }
    }
}

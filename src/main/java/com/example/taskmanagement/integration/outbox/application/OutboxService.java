package com.example.taskmanagement.integration.outbox.application;

import com.example.taskmanagement.integration.outbox.api.OutboxConsumerOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxMessage;
import com.example.taskmanagement.integration.outbox.api.OutboxOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxRetryOperations;
import com.example.taskmanagement.integration.outbox.domain.DeliveryRetryPolicy;
import com.example.taskmanagement.integration.outbox.persistence.OutboxMessageEntity;
import com.example.taskmanagement.integration.outbox.persistence.OutboxMessageRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class OutboxService implements OutboxOperations, OutboxConsumerOperations, OutboxRetryOperations {
    private final OutboxMessageRepository messages;
    private final Clock clock;

    public OutboxService(OutboxMessageRepository messages, Clock clock) {
        this.messages = messages;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OutboxMessage> findMessage(long id) {
        return messages.findById(id).map(row -> new OutboxMessage(row.id(), row.eventType(), row.payload()));
    }

    @Override
    public void append(String eventType, String payload) {
        messages.saveAndFlush(new OutboxMessageEntity(eventType, payload,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OutboxMessage> claimUnprocessed(int limit) {
        return map(messages.findUnprocessed(clock.instant(), PageRequest.of(0, size(limit))));
    }

    @Override
    public void markProcessed(long id) {
        messages.findById(id).ifPresent(row -> {
            row.markProcessed(clock.instant().truncatedTo(ChronoUnit.MICROS));
            messages.saveAndFlush(row);
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OutboxMessage> claimTelegramUnprocessed(int limit) {
        return map(messages.findTelegramUnprocessed(clock.instant(), PageRequest.of(0, size(limit))));
    }

    @Override
    public void markTelegramProcessed(long id) {
        messages.findById(id).ifPresent(row -> {
            row.markTelegramProcessed(clock.instant().truncatedTo(ChronoUnit.MICROS));
            messages.saveAndFlush(row);
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OutboxMessage> claimEmailUnprocessed(int limit) {
        return map(messages.findEmailUnprocessed(clock.instant(), PageRequest.of(0, size(limit))));
    }

    @Override
    public void markEmailProcessed(long id) {
        messages.findById(id).ifPresent(row -> {
            row.markEmailProcessed(clock.instant().truncatedTo(ChronoUnit.MICROS));
            messages.saveAndFlush(row);
        });
    }

    @Override
    public void defer(long messageId, Channel channel) {
        Objects.requireNonNull(channel, "channel");
        messages.lockById(messageId).ifPresent(row -> {
            row.defer(channel, clock.instant().truncatedTo(ChronoUnit.MICROS), new DeliveryRetryPolicy());
            messages.saveAndFlush(row);
        });
    }

    private static List<OutboxMessage> map(List<OutboxMessageEntity> rows) {
        return rows.stream().map(row -> new OutboxMessage(row.id(), row.eventType(), row.payload())).toList();
    }

    private static int size(int limit) {
        return Math.min(Math.max(limit, 1), 100);
    }
}

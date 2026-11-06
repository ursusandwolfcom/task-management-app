package com.example.taskmanagement.notification.application;

import com.example.taskmanagement.task.api.TaskDeadlineOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notifications.deadlines.enabled", havingValue = "true", matchIfMissing = true)
public class DeadlineReminderScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(DeadlineReminderScheduler.class);
    private final TaskDeadlineOperations tasks;
    private final DeadlineReminderProducer producer;
    private final int batchSize;
    private long cursor;

    public DeadlineReminderScheduler(TaskDeadlineOperations tasks, DeadlineReminderProducer producer,
            @Value("${notifications.deadlines.batch-size:100}") int batchSize) {
        this.tasks = tasks;
        this.producer = producer;
        this.batchSize = Math.clamp(batchSize, 1, 100);
    }

    @Scheduled(fixedDelayString = "${notifications.deadlines.delay-ms:30000}",
            initialDelayString = "${notifications.deadlines.initial-delay-ms:30000}")
    public synchronized void scan() {
        var ids = tasks.scanAfter(cursor, batchSize);
        if (ids.isEmpty()) {
            cursor = 0;
            return;
        }
        for (long id : ids) {
            try {
                producer.produce(id);
            } catch (RuntimeException exception) {
                // No payload, provider details or credentials in background failure logs.
                LOG.warn("Deadline reminder production failed for task {}", id);
            }
            cursor = id;
        }
    }
}

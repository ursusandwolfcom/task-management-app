package com.example.taskmanagement.config;

import com.example.taskmanagement.integration.email.api.EmailOperations;
import com.example.taskmanagement.integration.telegram.api.TelegramOperations;
import com.example.taskmanagement.notification.api.NotificationOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notifications.delivery.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDeliveryScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationDeliveryScheduler.class);
    private final NotificationOperations notifications;
    private final EmailOperations email;
    private final TelegramOperations telegram;
    private final int batchSize;

    public NotificationDeliveryScheduler(NotificationOperations notifications, EmailOperations email,
            TelegramOperations telegram, @Value("${notifications.delivery.batch-size:100}") int batchSize) {
        this.notifications = notifications;
        this.email = email;
        this.telegram = telegram;
        this.batchSize = Math.clamp(batchSize, 1, 100);
    }

    @Scheduled(fixedDelayString = "${notifications.delivery.delay-ms:30000}",
            initialDelayString = "${notifications.delivery.initial-delay-ms:30000}")
    public void dispatchPending() {
        run("in-app", () -> notifications.projectPending(batchSize));
        run("email", () -> email.deliverPending(batchSize));
        run("telegram", () -> telegram.deliverPending(batchSize));
    }

    private void run(String channel, Runnable dispatch) {
        try {
            dispatch.run();
        } catch (RuntimeException exception) {
            LOG.warn("Background notification channel {} failed", channel);
        }
    }
}

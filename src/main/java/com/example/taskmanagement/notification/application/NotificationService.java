package com.example.taskmanagement.notification.application;

import com.example.taskmanagement.integration.outbox.api.OutboxConsumerOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxEvents;
import com.example.taskmanagement.integration.outbox.api.OutboxMessage;
import com.example.taskmanagement.notification.api.NotificationException;
import com.example.taskmanagement.notification.api.NotificationOperations;
import com.example.taskmanagement.notification.persistence.NotificationEntity;
import com.example.taskmanagement.notification.persistence.NotificationRepository;
import com.example.taskmanagement.shared.persistence.PageRequests;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@Transactional
public class NotificationService implements NotificationOperations {
    private final NotificationRepository notifications;
    private final OutboxConsumerOperations outbox;
    private final ObjectMapper json;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, OutboxConsumerOperations outbox,
            ObjectMapper json, Clock clock) {
        this.notifications = notifications;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPage list(long actorId, boolean unreadOnly, int page, int size) {
        var paging = PageRequests.descending(page, size, "id",
                () -> new NotificationException(NotificationException.Reason.VALIDATION_ERROR));
        var result = unreadOnly
                ? notifications.findByUserIdAndReadAtIsNull(actorId, paging)
                : notifications.findByUserId(actorId, paging);
        return new NotificationPage(result.getContent().stream().map(NotificationEntity::view).toList(),
                page, size, result.getTotalElements(), result.getTotalPages(),
                notifications.countByUserIdAndReadAtIsNull(actorId));
    }

    @Override
    public void markRead(long actorId, long notificationId) {
        var row = notifications.findById(notificationId)
                .orElseThrow(() -> new NotificationException(NotificationException.Reason.NOTIFICATION_NOT_FOUND));
        if (row.userId() != actorId) {
            throw new NotificationException(NotificationException.Reason.NOTIFICATION_NOT_FOUND);
        }
        row.markRead(clock.instant().truncatedTo(ChronoUnit.MICROS));
        notifications.saveAndFlush(row);
    }

    @Override
    public void markAllRead(long actorId) {
        notifications.markAllRead(actorId, clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    @Override
    public int projectPending(int limit) {
        var batch = outbox.claimUnprocessed(limit);
        int projected = 0;
        for (OutboxMessage message : batch) {
            project(message);
            outbox.markProcessed(message.id());
            projected++;
        }
        return projected;
    }

    private void project(OutboxMessage message) {
        JsonNode payload = parse(message.payload());
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (OutboxEvents.TASK_ASSIGNED.equals(message.eventType())) {
            long recipient = payload.path("assigneeId").asLong(0);
            long actor = payload.path("actorId").asLong(0);
            if (recipient > 0 && recipient != actor) {
                notifications.saveAndFlush(new NotificationEntity(recipient, message.eventType(),
                        payload.path("projectId").asLong(), payload.path("taskId").asLong(),
                        "Назначена задача", truncate(payload.path("taskName").asString("Задача")), now));
            }
            return;
        }
        if (OutboxEvents.COMMENT_CREATED.equals(message.eventType())) {
            long recipient = payload.path("assigneeId").asLong(0);
            long author = payload.path("authorId").asLong(0);
            if (recipient > 0 && recipient != author) {
                notifications.saveAndFlush(new NotificationEntity(recipient, message.eventType(),
                        payload.path("projectId").asLong(), payload.path("taskId").asLong(),
                        "Новый комментарий", truncate(payload.path("preview").asString("Комментарий")), now));
            }
            return;
        }
        if (OutboxEvents.TASK_STARTED.equals(message.eventType())
                || OutboxEvents.TASK_COMPLETED.equals(message.eventType())) {
            long actor = payload.path("actorId").asLong(0);
            long assignee = payload.path("assigneeId").asLong(0);
            long owner = payload.path("ownerId").asLong(0);
            long projectId = payload.path("projectId").asLong();
            long taskId = payload.path("taskId").asLong();
            String name = truncate(payload.path("taskName").asString("Задача"));
            boolean started = OutboxEvents.TASK_STARTED.equals(message.eventType());
            String title = started ? "Задача запущена" : "Задача завершена";
            if (assignee > 0 && assignee != actor) {
                notifications.saveAndFlush(new NotificationEntity(assignee, message.eventType(), projectId, taskId,
                        title, name, now));
            }
            if (!started && owner > 0 && owner != actor && owner != assignee) {
                notifications.saveAndFlush(new NotificationEntity(owner, message.eventType(), projectId, taskId,
                        title, name, now));
            }
        }
    }

    private JsonNode parse(String payload) {
        try {
            return json.readTree(payload);
        } catch (RuntimeException exception) {
            throw new NotificationException(NotificationException.Reason.VALIDATION_ERROR);
        }
    }

    private static String truncate(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return "—";
        }
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}

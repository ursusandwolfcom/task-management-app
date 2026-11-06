package com.example.taskmanagement.integration.outbox.api;

import java.util.LinkedHashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Shared outbox → push-channel projection (Telegram, Email, …).
 * Transport adapters stay channel-specific strategies.
 */
public final class OutboxPushProjection {
    public record Rendered(String title, String body) {
    }

    private OutboxPushProjection() {
    }

    public static boolean isPushEvent(String eventType) {
        return OutboxEvents.TASK_ASSIGNED.equals(eventType)
                || OutboxEvents.COMMENT_CREATED.equals(eventType)
                || OutboxEvents.TASK_COMPLETED.equals(eventType)
                || OutboxEvents.DEADLINE_REMINDER.equals(eventType)
                || OutboxEvents.TASK_FAILED.equals(eventType);
    }

    public static long actorId(String eventType, JsonNode payload) {
        if (OutboxEvents.COMMENT_CREATED.equals(eventType)) {
            return payload.path("authorId").asLong(0);
        }
        return payload.path("actorId").asLong(0);
    }

    public static Set<Long> recipientIds(String eventType, JsonNode payload) {
        Set<Long> recipients = new LinkedHashSet<>();
        if (OutboxEvents.DEADLINE_REMINDER.equals(eventType)) {
            long recipient = payload.path("recipientId").asLong(0);
            if (recipient > 0) {
                recipients.add(recipient);
            }
            return recipients;
        }
        long assignee = payload.path("assigneeId").asLong(0);
        if (assignee > 0) {
            recipients.add(assignee);
        }
        if (OutboxEvents.TASK_COMPLETED.equals(eventType) || OutboxEvents.TASK_FAILED.equals(eventType)) {
            long owner = payload.path("ownerId").asLong(0);
            if (owner > 0) {
                recipients.add(owner);
            }
        }
        return recipients;
    }

    public static Rendered render(String eventType, JsonNode payload, String appPublicBase) {
        String title = switch (eventType) {
            case OutboxEvents.DEADLINE_REMINDER -> "Task deadline: " + payload.path("dueDate").asString("");
            case OutboxEvents.TASK_ASSIGNED -> "Назначена задача";
            case OutboxEvents.COMMENT_CREATED -> "Новый комментарий";
            case OutboxEvents.TASK_COMPLETED -> "Задача завершена";
            case OutboxEvents.TASK_FAILED -> "Задача провалена";
            default -> "Обновление ProjectFlow";
        };
        String detail = truncate(switch (eventType) {
            case OutboxEvents.COMMENT_CREATED -> payload.path("preview").asString("Комментарий");
            default -> payload.path("taskName").asString("Задача");
        });
        long taskId = payload.path("taskId").asLong(0);
        String link = taskId > 0 ? taskLink(taskId, appPublicBase) : "";
        String body = link.isEmpty() ? detail : detail + "\n" + link;
        return new Rendered(title, body);
    }

    private static String taskLink(long taskId, String appPublicBase) {
        String base = appPublicBase == null ? "" : appPublicBase.strip();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) {
            return "#/tasks/" + taskId;
        }
        return base + "/#/tasks/" + taskId;
    }

    private static String truncate(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return "—";
        }
        return text.length() <= 200 ? text : text.substring(0, 200);
    }
}

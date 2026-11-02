package com.example.taskmanagement.notification.api;

import java.time.Instant;

public record NotificationView(long id, String eventType, long projectId, long taskId, String title, String body,
        Instant createdAt, Instant readAt) {
}

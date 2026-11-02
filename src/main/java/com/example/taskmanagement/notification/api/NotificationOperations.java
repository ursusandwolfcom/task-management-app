package com.example.taskmanagement.notification.api;

import java.util.List;

public interface NotificationOperations {
    record NotificationPage(List<NotificationView> content, int page, int size, long totalElements, int totalPages,
            long unreadCount) {
        public NotificationPage {
            content = List.copyOf(content);
        }
    }

    NotificationPage list(long actorId, boolean unreadOnly, int page, int size);

    void markRead(long actorId, long notificationId);

    void markAllRead(long actorId);

    /** Drain outbox into in-app notifications; safe to call repeatedly. */
    int projectPending(int limit);
}

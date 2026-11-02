package com.example.taskmanagement.notification.persistence;

import com.example.taskmanagement.notification.api.NotificationView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notifications", schema = "task_management")
public class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;
    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    private String eventType;
    @Column(name = "project_id", nullable = false, updatable = false)
    private long projectId;
    @Column(name = "task_id", nullable = false, updatable = false)
    private long taskId;
    @Column(nullable = false, length = 200, updatable = false)
    private String title;
    @Column(nullable = false, length = 1000, updatable = false)
    private String body;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "read_at")
    private Instant readAt;

    protected NotificationEntity() {
    }

    public NotificationEntity(long userId, String eventType, long projectId, long taskId, String title, String body,
            Instant createdAt) {
        this.userId = userId;
        this.eventType = eventType;
        this.projectId = projectId;
        this.taskId = taskId;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
    }

    public long userId() {
        return userId;
    }

    public Instant readAt() {
        return readAt;
    }

    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public NotificationView view() {
        return new NotificationView(id, eventType, projectId, taskId, title, body, createdAt, readAt);
    }
}

package com.example.taskmanagement.integration.outbox.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "outbox_messages", schema = "task_management")
public class OutboxMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    private String eventType;
    @Column(nullable = false, length = 4000, updatable = false)
    private String payload;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "processed_at")
    private Instant processedAt;

    protected OutboxMessageEntity() {
    }

    public OutboxMessageEntity(String eventType, String payload, Instant createdAt) {
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long id() {
        return id;
    }

    public String eventType() {
        return eventType;
    }

    public String payload() {
        return payload;
    }

    public void markProcessed(Instant at) {
        this.processedAt = at;
    }
}

package com.example.taskmanagement.integration.outbox.api;

public record OutboxMessage(long id, String eventType, String payload) {
}

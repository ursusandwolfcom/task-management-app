package com.example.taskmanagement.integration.outbox.api;

/** Shared event type constants for producers and consumers. */
public final class OutboxEvents {
    public static final String TASK_ASSIGNED = "task.assigned";
    public static final String COMMENT_CREATED = "comment.created";

    private OutboxEvents() {
    }
}

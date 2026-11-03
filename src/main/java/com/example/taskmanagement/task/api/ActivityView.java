package com.example.taskmanagement.task.api;

import java.time.Instant;

public record ActivityView(long id, long projectId, long taskId, long actorId, String actionType, String summary,
        Instant createdAt) {
}

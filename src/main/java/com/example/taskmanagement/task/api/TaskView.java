package com.example.taskmanagement.task.api;

import com.example.taskmanagement.task.domain.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;

public record TaskView(long id, long projectId, String name, String description, TaskPriority priority,
        Integer estimatedMinutes, LocalDate dueDate, Long assigneeId, TaskStatus status,
        Instant actualStart, Instant actualEnd, int attemptCount, long version) {
}

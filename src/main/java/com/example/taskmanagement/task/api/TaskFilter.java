package com.example.taskmanagement.task.api;

import com.example.taskmanagement.task.domain.TaskStatus;
import java.time.LocalDate;

/** Optional list constraints; null fields are ignored. dueFrom/dueTo require a non-null dueDate. */
public record TaskFilter(TaskStatus status, Long assigneeId, LocalDate dueFrom, LocalDate dueTo, Long labelId,
        TaskBucket bucket) {
    public static TaskFilter empty() {
        return new TaskFilter(null, null, null, null, null, null);
    }
}

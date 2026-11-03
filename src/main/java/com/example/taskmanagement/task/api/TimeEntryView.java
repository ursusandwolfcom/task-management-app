package com.example.taskmanagement.task.api;

import java.time.Instant;
import java.time.LocalDate;

public record TimeEntryView(
        long id,
        long projectId,
        long taskId,
        long userId,
        LocalDate workDate,
        int minutes,
        String note,
        Instant createdAt) {
}

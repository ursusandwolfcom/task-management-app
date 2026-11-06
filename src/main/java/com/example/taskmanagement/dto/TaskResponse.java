package com.example.taskmanagement.dto;

import com.example.taskmanagement.model.Priority;
import com.example.taskmanagement.model.TaskStatus;
import java.time.LocalDate;
import java.util.Set;

public record TaskResponse(
        Long id, String name, String description, Priority priority, TaskStatus status,
        LocalDate dueDate, Long projectId, Long assigneeId, Set<Long> labelIds) {
}

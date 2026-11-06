package com.example.taskmanagement.dto;

import com.example.taskmanagement.model.Priority;
import com.example.taskmanagement.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

public record TaskRequest(
        @NotBlank @Size(max = 200) String name, @Size(max = 4000) String description,
        @NotNull Priority priority, @NotNull TaskStatus status, LocalDate dueDate,
        @NotNull Long projectId, Long assigneeId, @NotNull @Size(max = 100) Set<@NotNull Long> labelIds) {
}

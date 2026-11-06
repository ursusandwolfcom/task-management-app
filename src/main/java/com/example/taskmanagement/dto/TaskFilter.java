package com.example.taskmanagement.dto;

import com.example.taskmanagement.model.Priority;
import com.example.taskmanagement.model.TaskStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record TaskFilter(
        @Size(max = 200) String name, TaskStatus status, Priority priority, Long assigneeId, Long labelId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo) {
}

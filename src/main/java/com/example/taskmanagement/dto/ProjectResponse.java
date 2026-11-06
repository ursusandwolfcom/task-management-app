package com.example.taskmanagement.dto;

import com.example.taskmanagement.model.ProjectStatus;
import java.time.LocalDate;
import java.util.Set;

public record ProjectResponse(
        Long id, String name, String description, LocalDate startDate, LocalDate endDate,
        ProjectStatus status, Long ownerId, Set<Long> memberIds) {
}

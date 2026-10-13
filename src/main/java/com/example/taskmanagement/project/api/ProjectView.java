package com.example.taskmanagement.project.api;

import java.time.LocalDate;

public record ProjectView(long id, String name, String description, LocalDate startDate,
        LocalDate endDate, String timezone, ProjectStatus status, long ownerId, long version,
        long inputRevision, long calendarRevision) {
}

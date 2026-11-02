package com.example.taskmanagement.task.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Portfolio risk overview for projects the actor can read. */
public interface PortfolioOperations {

    enum Risk { ON_TRACK, AT_RISK, OVERDUE }

    record ProjectRisk(
            long projectId,
            String name,
            String status,
            String timezone,
            LocalDate endDate,
            long openTasks,
            long criticalTasks,
            long durationMinutes,
            boolean projectDeadlineOverrun,
            boolean hasTaskDeadlineOverrun,
            Risk risk) {
    }

    record PortfolioPage(
            Instant calculatedAt,
            List<ProjectRisk> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
        public PortfolioPage {
            content = List.copyOf(content);
        }
    }

    PortfolioPage overview(long actorId, int page, int size);
}

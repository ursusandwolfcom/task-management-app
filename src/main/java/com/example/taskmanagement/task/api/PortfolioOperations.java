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
            Risk risk,
            long plannedMinutes,
            long completedPlannedMinutes,
            int completionPercent) {
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

    record AssigneeLoad(long userId, long openTasks, long estimatedMinutes, long overdueTasks,
            long contendedTasks) {
    }

    record WorkloadSnapshot(Instant calculatedAt, List<AssigneeLoad> assignees) {
        public WorkloadSnapshot {
            assignees = List.copyOf(assignees);
        }
    }

    PortfolioPage overview(long actorId, int page, int size);

    /** Open assigned work across accessible projects; no resource calendar leveling. */
    WorkloadSnapshot workload(long actorId);
}

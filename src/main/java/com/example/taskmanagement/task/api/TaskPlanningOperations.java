package com.example.taskmanagement.task.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;

/** Dependency readiness is a recommendation, not permission or a resource reservation to start a task. */
public interface TaskPlanningOperations {
    record ExecutionOrder(long projectId, long inputRevision, List<Long> taskIds) {
        public ExecutionOrder {
            taskIds = List.copyOf(taskIds);
        }
    }

    record BlockedTask(long taskId, String code, List<Long> predecessorIds) {
        public BlockedTask {
            predecessorIds = List.copyOf(predecessorIds);
        }
    }

    record AvailableTasks(long projectId, long inputRevision, List<TaskView> tasks, List<BlockedTask> blockingReasons) {
        public AvailableTasks {
            tasks = List.copyOf(tasks);
            blockingReasons = List.copyOf(blockingReasons);
        }
    }

    record NextTask(long projectId, long inputRevision, TaskView task) {
    }

    record GanttTask(
            long taskId,
            long durationMinutes,
            Instant earlyStart,
            Instant earlyFinish,
            Instant lateStart,
            Instant lateFinish,
            long floatMinutes,
            boolean critical,
            boolean deadlineOverrun,
            boolean resourceContention) {
    }

    record CriticalDependency(long predecessorId, long successorId) {
    }

    record ProjectGantt(
            long projectId,
            long inputRevision,
            long calendarRevision,
            String timezone,
            Instant calculatedAt,
            Instant projectStart,
            Instant projectFinish,
            long durationMinutes,
            boolean projectDeadlineOverrun,
            List<GanttTask> tasks,
            List<Long> criticalTaskIds,
            List<CriticalDependency> criticalEdges) {
        public ProjectGantt {
            tasks = List.copyOf(tasks);
            criticalTaskIds = List.copyOf(criticalTaskIds);
            criticalEdges = List.copyOf(criticalEdges);
        }
    }

    /** Optional estimate override; null minutes means a zero-length milestone for the scenario. */
    record EstimateOverride(@Positive long taskId, @Min(15) @Max(525600) Integer estimatedMinutes) {
    }

    record WhatIfRequest(@NotNull @Valid List<EstimateOverride> estimates) {
        public WhatIfRequest {
            estimates = List.copyOf(estimates);
        }
    }

    ExecutionOrder executionOrder(long actorId, long projectId);

    AvailableTasks availableTasks(long actorId, long projectId);

    NextTask nextTask(long actorId, long projectId);

    /**
     * @param resourceLeveling when true, serialize overlapping assignee windows (hard leveling
     *                         projection respecting dependencies; does not persist). Member calendars
     *                         apply to assigned work. Late dates/critical edges retain the unlimited-resource
     *                         CPM baseline; float shows remaining baseline slack, clamped to zero.
     */
    ProjectGantt gantt(long actorId, long projectId, boolean resourceLeveling);

    /** CPM with in-memory estimate overrides; never persists task changes. */
    ProjectGantt whatIf(long actorId, long projectId, @NotNull @Valid WhatIfRequest request,
            boolean resourceLeveling);
}

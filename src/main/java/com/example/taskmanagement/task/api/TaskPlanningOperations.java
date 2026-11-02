package com.example.taskmanagement.task.api;

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
            boolean deadlineOverrun) {
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

    ExecutionOrder executionOrder(long actorId, long projectId);

    AvailableTasks availableTasks(long actorId, long projectId);

    NextTask nextTask(long actorId, long projectId);

    ProjectGantt gantt(long actorId, long projectId);
}

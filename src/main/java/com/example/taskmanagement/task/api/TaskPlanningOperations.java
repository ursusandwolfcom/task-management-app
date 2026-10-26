package com.example.taskmanagement.task.api;

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

    ExecutionOrder executionOrder(long actorId, long projectId);

    AvailableTasks availableTasks(long actorId, long projectId);

    NextTask nextTask(long actorId, long projectId);
}

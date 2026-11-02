package com.example.taskmanagement.task.api;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/** Mutations compare the project's inputRevision, not the metadata version of either task. */
public interface TaskDependencyOperations {
    record Change(long projectId, long taskId, long dependsOnTaskId, long inputRevision) {
    }

    record Predecessors(long taskId, long projectId, long inputRevision, List<Long> dependsOnTaskIds) {
        public Predecessors {
            dependsOnTaskIds = List.copyOf(dependsOnTaskIds);
        }
    }

    Predecessors list(long actorId, @Positive long taskId);

    Change add(long actorId, @Positive long taskId, @Positive long dependsOnTaskId,
            @PositiveOrZero long inputRevision);

    Change remove(long actorId, @Positive long taskId, @Positive long dependsOnTaskId,
            @PositiveOrZero long inputRevision);
}

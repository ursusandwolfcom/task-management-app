package com.example.taskmanagement.task.api;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Mutations compare the project's inputRevision, not the metadata version of either task. */
public interface TaskDependencyOperations {
    record Change(long projectId, long taskId, long dependsOnTaskId, long inputRevision) {
    }

    Change add(long actorId, @Positive long taskId, @Positive long dependsOnTaskId,
            @PositiveOrZero long inputRevision);

    Change remove(long actorId, @Positive long taskId, @Positive long dependsOnTaskId,
            @PositiveOrZero long inputRevision);
}

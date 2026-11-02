package com.example.taskmanagement.task.api;

import com.example.taskmanagement.task.domain.ExecutionAttempt;
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy.Command;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public interface TaskOperations {
    record TaskPage(List<TaskView> content, int page, int size, long totalElements, int totalPages) {
        public TaskPage {
            content = List.copyOf(content);
        }
    }

    record AttemptPage(List<ExecutionAttempt> content, int page, int size, long totalElements, int totalPages) {
        public AttemptPage {
            content = List.copyOf(content);
        }
    }

    TaskView create(long actorId, long projectId, @NotNull @Valid TaskData data);

    TaskView read(long actorId, long taskId);

    TaskPage list(long actorId, long projectId, @NotNull TaskFilter filter, int page, int size);

    /** Tasks assigned to the actor across accessible projects (ADMIN sees all assigned to self). */
    TaskPage listAssigned(long actorId, @NotNull TaskFilter filter, int page, int size);

    TaskView replace(long actorId, long taskId, @NotNull @Valid TaskData data, @PositiveOrZero long version);

    TaskView execute(long actorId, long taskId, @NotNull Command command, @PositiveOrZero long version);

    AttemptPage attempts(long actorId, long taskId, int page, int size);

    void delete(long actorId, long taskId, @PositiveOrZero long version);
}

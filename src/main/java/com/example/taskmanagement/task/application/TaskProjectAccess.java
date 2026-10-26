package com.example.taskmanagement.task.application;

import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.project.api.ProjectException;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.persistence.TaskRepository;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Shared task-to-project access: foreign tasks remain indistinguishable from missing tasks. */
@Component
public class TaskProjectAccess {
    private final TaskRepository tasks;
    private final ProjectCoordination coordination;
    private final ProjectOperations projects;

    public TaskProjectAccess(TaskRepository tasks, ProjectCoordination coordination, ProjectOperations projects) {
        this.tasks = tasks;
        this.coordination = coordination;
        this.projects = projects;
    }

    ProjectView lock(long actorId, long taskId) {
        return taskProject(() -> coordination.lockAndAuthorize(actorId, projectId(taskId),
                ProjectCoordination.Permission.WRITE_TASKS));
    }

    ProjectView readable(long actorId, long taskId) {
        return taskProject(() -> projects.read(actorId, projectId(taskId)));
    }

    ProjectView readProject(long actorId, long projectId) {
        return projects.read(actorId, projectId);
    }

    private long projectId(long taskId) {
        return tasks.projectId(taskId).orElseThrow(() -> new TaskException(TaskException.Reason.TASK_NOT_FOUND));
    }

    private static ProjectView taskProject(Supplier<ProjectView> operation) {
        try {
            return operation.get();
        } catch (ProjectException exception) {
            if (exception.reason() == ProjectException.Reason.PROJECT_NOT_FOUND) {
                throw new TaskException(TaskException.Reason.TASK_NOT_FOUND);
            }
            throw exception;
        }
    }
}

package com.example.taskmanagement.collaboration.application;

import com.example.taskmanagement.collaboration.api.CollaborationException;
import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.project.api.ProjectException;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskView;
import org.springframework.stereotype.Component;

/** Collaboration uses only module APIs and repeats existence/access checks after waiting for the project lock. */
@Component
public class CollaborationAccess {
    private final TaskOperations tasks;
    private final ProjectCoordination coordination;

    public CollaborationAccess(TaskOperations tasks, ProjectCoordination coordination) {
        this.tasks = tasks;
        this.coordination = coordination;
    }

    TaskView task(long actorId, long taskId) {
        return tasks.read(actorId, taskId);
    }

    ProjectView lockTask(long actorId, long taskId) {
        var initial = task(actorId, taskId);
        var project = lockProject(actorId, initial.projectId(), CollaborationException.Reason.TASK_NOT_FOUND);
        task(actorId, taskId);
        return project;
    }

    ProjectView lockProject(long actorId, long projectId, CollaborationException.Reason hiddenReason) {
        try {
            return coordination.lockAndAuthorize(actorId, projectId, ProjectCoordination.Permission.WRITE_TASKS);
        } catch (ProjectException exception) {
            if (exception.reason() == ProjectException.Reason.PROJECT_NOT_FOUND) {
                throw new CollaborationException(hiddenReason);
            }
            throw exception;
        }
    }
}

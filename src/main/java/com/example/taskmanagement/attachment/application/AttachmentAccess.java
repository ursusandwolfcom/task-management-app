package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.project.api.ProjectException;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskView;
import org.springframework.stereotype.Component;

@Component
public class AttachmentAccess {
    private final TaskOperations tasks;
    private final ProjectCoordination coordination;

    public AttachmentAccess(TaskOperations tasks, ProjectCoordination coordination) {
        this.tasks = tasks;
        this.coordination = coordination;
    }

    TaskView task(long actorId, long taskId) {
        return tasks.read(actorId, taskId);
    }

    ProjectView lockTask(long actorId, long taskId) {
        var initial = task(actorId, taskId);
        var project = lockProject(actorId, initial.projectId(), AttachmentException.Reason.TASK_NOT_FOUND);
        task(actorId, taskId);
        return project;
    }

    ProjectView lockProject(long actorId, long projectId, AttachmentException.Reason hiddenReason) {
        try {
            return coordination.lockAndAuthorize(actorId, projectId, ProjectCoordination.Permission.WRITE_TASKS);
        } catch (ProjectException exception) {
            if (exception.reason() == ProjectException.Reason.PROJECT_NOT_FOUND) {
                throw new AttachmentException(hiddenReason);
            }
            throw exception;
        }
    }
}

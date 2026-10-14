package com.example.taskmanagement.task.application;

import com.example.taskmanagement.project.api.ProjectContentGuard;
import com.example.taskmanagement.project.api.ProjectException;
import com.example.taskmanagement.task.persistence.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class TaskProjectGuard implements ProjectContentGuard {
    private final TaskRepository tasks;

    public TaskProjectGuard(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override
    public void beforeRemoveMember(long projectId, long userId) {
        if (tasks.existsByProjectIdAndAssigneeId(projectId, userId)) {
            throw new ProjectException(ProjectException.Reason.MEMBER_HAS_ASSIGNMENTS);
        }
    }

    @Override
    public void beforeDelete(long projectId) {
        if (tasks.existsByProjectId(projectId)) {
            throw new ProjectException(ProjectException.Reason.PROJECT_NOT_EMPTY);
        }
    }
}

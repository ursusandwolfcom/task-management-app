package com.example.taskmanagement.task.application;

import com.example.taskmanagement.task.api.ActivityOperations;
import com.example.taskmanagement.task.persistence.ActivityEntity;
import com.example.taskmanagement.task.persistence.ActivityRepository;
import com.example.taskmanagement.shared.persistence.PageRequests;
import com.example.taskmanagement.project.api.ProjectException;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.persistence.TaskRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class ActivityService implements ActivityOperations {
    private final ActivityRepository activities;
    private final TaskRepository tasks;
    private final ProjectOperations projects;
    private final Clock clock;

    public ActivityService(ActivityRepository activities, TaskRepository tasks, ProjectOperations projects,
            Clock clock) {
        this.activities = activities;
        this.tasks = tasks;
        this.projects = projects;
        this.clock = clock;
    }

    @Override
    public void record(long actorId, long projectId, long taskId, String actionType, String summary) {
        activities.saveAndFlush(new ActivityEntity(projectId, taskId, actorId, actionType, truncate(summary),
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityPage list(long actorId, long taskId, int page, int size) {
        long projectId = tasks.projectId(taskId)
                .orElseThrow(() -> new TaskException(TaskException.Reason.TASK_NOT_FOUND));
        try {
            projects.read(actorId, projectId);
        } catch (ProjectException exception) {
            if (exception.reason() == ProjectException.Reason.PROJECT_NOT_FOUND) {
                throw new TaskException(TaskException.Reason.TASK_NOT_FOUND);
            }
            throw exception;
        }
        var paging = PageRequests.descending(page, size, "id",
                () -> new TaskException(TaskException.Reason.VALIDATION_ERROR));
        var result = activities.findByTaskId(taskId, paging);
        return new ActivityPage(result.getContent().stream().map(ActivityEntity::view).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    private static String truncate(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return "—";
        }
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}

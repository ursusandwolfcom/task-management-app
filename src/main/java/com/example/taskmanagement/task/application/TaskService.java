package com.example.taskmanagement.task.application;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.project.api.ProjectCoordination.Permission;
import com.example.taskmanagement.project.api.ProjectException;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.domain.TaskExecution;
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy;
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy.Command;
import com.example.taskmanagement.task.domain.TaskStatus;
import com.example.taskmanagement.task.persistence.AttemptEntity;
import com.example.taskmanagement.task.persistence.AttemptRepository;
import com.example.taskmanagement.task.persistence.TaskEntity;
import com.example.taskmanagement.task.persistence.TaskRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class TaskService implements TaskOperations {
    private final TaskRepository tasks;
    private final AttemptRepository attempts;
    private final ProjectCoordination coordination;
    private final ProjectOperations projects;
    private final IdentityOperations identity;
    private final EntityManager entityManager;
    private final Validator validator;
    private final Clock clock;

    public TaskService(TaskRepository tasks, AttemptRepository attempts, ProjectCoordination coordination,
            ProjectOperations projects, IdentityOperations identity, EntityManager entityManager,
            Validator validator, Clock clock) {
        this.tasks = tasks;
        this.attempts = attempts;
        this.coordination = coordination;
        this.projects = projects;
        this.identity = identity;
        this.entityManager = entityManager;
        this.validator = validator;
        this.clock = clock;
    }

    @Override
    public TaskView create(long actorId, long projectId, TaskData data) {
        coordination.lockAndAuthorize(actorId, projectId, Permission.WRITE_TASKS);
        validateData(projectId, data);
        var task = tasks.saveAndFlush(new TaskEntity(projectId, data));
        coordination.markInputChanged(actorId, projectId);
        return task.view();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskView read(long actorId, long taskId) {
        long projectId = projectId(taskId);
        readable(actorId, projectId);
        return found(taskId).view();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskPage list(long actorId, long projectId, int page, int size) {
        projects.read(actorId, projectId);
        var result = tasks.findByProjectId(projectId, paging(page, size, "id"));
        return new TaskPage(result.getContent().stream().map(TaskEntity::view).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public TaskView replace(long actorId, long taskId, TaskData data, long version) {
        var project = lock(actorId, taskId);
        var task = managed(taskId, version);
        validateData(project.id(), data);
        var previous = task.view();
        if (!Objects.equals(previous.assigneeId(), data.assigneeId())
                && previous.status() != TaskStatus.NOT_STARTED && previous.status() != TaskStatus.FAILED) {
            throw error(TaskException.Reason.ASSIGNMENT_CHANGE_FORBIDDEN);
        }
        task.update(data);
        tasks.saveAndFlush(task);
        coordination.markInputChanged(actorId, project.id());
        return task.view();
    }

    @Override
    public TaskView execute(long actorId, long taskId, Command command, long version) {
        var project = lock(actorId, taskId);
        var task = managed(taskId, version);
        authorizeExecution(actorId, project, task.view(), command == Command.CANCEL);
        try {
            TaskLifecyclePolicy.nextStatus(task.view().status(), command);
        } catch (IllegalStateException invalid) {
            throw error(TaskException.Reason.INVALID_TASK_TRANSITION);
        }
        if (command == Command.START && task.view().assigneeId() == null) {
            throw error(TaskException.Reason.ASSIGNEE_REQUIRED);
        }
        boolean ready = command != Command.START || tasks.incompletePredecessors(taskId) == 0;
        if (!ready) {
            throw error(TaskException.Reason.DEPENDENCIES_NOT_COMPLETED);
        }
        var stored = attempts.findByTaskIdOrderByNumberAsc(taskId);
        var execution = TaskExecution.restore(task.view().status(), stored.stream().map(AttemptEntity::view).toList());
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var next = switch (command) {
            case START -> execution.start(now, ready);
            case COMPLETE -> execution.complete(now);
            case FAIL -> execution.fail(now);
            case CANCEL -> execution.cancel(now);
            case RETRY -> execution.retry();
        };
        if (next.attempts().size() > stored.size()) {
            attempts.save(new AttemptEntity(taskId, next.attempts().getLast()));
        } else if (!stored.isEmpty()) {
            stored.getLast().finish(next.attempts().getLast());
        }
        task.execute(next);
        tasks.saveAndFlush(task);
        coordination.markInputChanged(actorId, project.id());
        return task.view();
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptPage attempts(long actorId, long taskId, int page, int size) {
        readable(actorId, projectId(taskId));
        var result = attempts.findByTaskId(taskId, paging(page, size, "number"));
        return new AttemptPage(result.getContent().stream().map(AttemptEntity::view).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public void delete(long actorId, long taskId, long version) {
        var project = lock(actorId, taskId);
        var task = managed(taskId, version);
        authorizeExecution(actorId, project, task.view(), true);
        if ((task.view().status() != TaskStatus.NOT_STARTED && task.view().status() != TaskStatus.CANCELLED)
                || task.view().attemptCount() != 0) {
            throw error(TaskException.Reason.TASK_DELETE_FORBIDDEN);
        }
        if (tasks.dependentCount(taskId) > 0) {
            throw error(TaskException.Reason.TASK_HAS_DEPENDENTS);
        }
        tasks.deleteIncomingEdges(taskId);
        tasks.delete(task);
        tasks.flush();
        coordination.markInputChanged(actorId, project.id());
    }

    private ProjectView lock(long actorId, long taskId) {
        try {
            return coordination.lockAndAuthorize(actorId, projectId(taskId), Permission.WRITE_TASKS);
        } catch (ProjectException exception) {
            if (exception.reason() == ProjectException.Reason.PROJECT_NOT_FOUND) {
                throw error(TaskException.Reason.TASK_NOT_FOUND);
            }
            throw exception;
        }
    }

    private void readable(long actorId, long projectId) {
        try {
            projects.read(actorId, projectId);
        } catch (ProjectException exception) {
            if (exception.reason() == ProjectException.Reason.PROJECT_NOT_FOUND) {
                throw error(TaskException.Reason.TASK_NOT_FOUND);
            }
            throw exception;
        }
    }

    private long projectId(long taskId) {
        return tasks.projectId(taskId).orElseThrow(() -> error(TaskException.Reason.TASK_NOT_FOUND));
    }

    private TaskEntity found(long taskId) {
        return tasks.findById(taskId).orElseThrow(() -> error(TaskException.Reason.TASK_NOT_FOUND));
    }

    private TaskEntity managed(long taskId, long version) {
        var task = found(taskId);
        entityManager.refresh(task);
        if (task.view().version() != version) {
            throw error(TaskException.Reason.VERSION_CONFLICT);
        }
        return task;
    }

    private void authorizeExecution(long actorId, ProjectView project, TaskView task, boolean ownerOnly) {
        boolean privileged = project.ownerId() == actorId || identity.currentRole(actorId) == UserRole.ADMIN;
        if (!privileged && (ownerOnly || !Objects.equals(task.assigneeId(), actorId))) {
            throw error(TaskException.Reason.FORBIDDEN);
        }
    }

    private void validateData(long projectId, TaskData data) {
        if (!validator.validate(data).isEmpty()) {
            throw error(TaskException.Reason.VALIDATION_ERROR);
        }
        data.validateDate();
        if (data.assigneeId() != null && !coordination.isMember(projectId, data.assigneeId())) {
            throw error(TaskException.Reason.ASSIGNEE_NOT_MEMBER);
        }
    }

    private static PageRequest paging(int page, int size, String property) {
        if (page < 0 || page > 1000000 || size < 1 || size > 100) {
            throw error(TaskException.Reason.VALIDATION_ERROR);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, property));
    }

    private static TaskException error(TaskException.Reason reason) {
        return new TaskException(reason);
    }
}

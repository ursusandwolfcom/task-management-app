package com.example.taskmanagement.task.application;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.integration.outbox.api.OutboxEvents;
import com.example.taskmanagement.integration.outbox.api.OutboxOperations;
import com.example.taskmanagement.shared.persistence.PageRequests;
import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.project.api.ProjectCoordination.Permission;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.api.TaskFilter;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.domain.TaskExecution;
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy;
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy.Command;
import com.example.taskmanagement.task.domain.TaskStatus;
import com.example.taskmanagement.task.persistence.AttemptEntity;
import com.example.taskmanagement.task.persistence.AttemptRepository;
import com.example.taskmanagement.task.persistence.TaskEntity;
import com.example.taskmanagement.task.persistence.TaskQueryRepository;
import com.example.taskmanagement.task.persistence.TaskRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@Transactional
public class TaskService implements TaskOperations {
    private final TaskRepository tasks;
    private final TaskQueryRepository queries;
    private final AttemptRepository attempts;
    private final ProjectCoordination coordination;
    private final TaskProjectAccess access;
    private final IdentityOperations identity;
    private final OutboxOperations outbox;
    private final ObjectMapper json;
    private final EntityManager entityManager;
    private final Validator validator;
    private final Clock clock;

    public TaskService(TaskRepository tasks, TaskQueryRepository queries, AttemptRepository attempts,
            ProjectCoordination coordination, TaskProjectAccess access, IdentityOperations identity,
            OutboxOperations outbox, ObjectMapper json, EntityManager entityManager, Validator validator,
            Clock clock) {
        this.tasks = tasks;
        this.queries = queries;
        this.attempts = attempts;
        this.coordination = coordination;
        this.access = access;
        this.identity = identity;
        this.outbox = outbox;
        this.json = json;
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
        var created = task.view();
        if (created.assigneeId() != null) {
            outbox.append(OutboxEvents.TASK_ASSIGNED, payload(actorId, created));
        }
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskView read(long actorId, long taskId) {
        access.readable(actorId, taskId);
        return found(taskId).view();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskPage list(long actorId, long projectId, TaskFilter filter, int page, int size) {
        access.readProject(actorId, projectId);
        var result = queries.inProject(projectId, filter == null ? TaskFilter.empty() : filter,
                paging(page, size, "id"));
        return new TaskPage(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public TaskPage listAssigned(long actorId, TaskFilter filter, int page, int size) {
        return listAssignedTo(actorId, actorId, filter, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskPage listAssignedTo(long actorId, long assigneeId, TaskFilter filter, int page, int size) {
        boolean admin = identity.currentRole(actorId) == UserRole.ADMIN;
        var result = queries.assignedTo(actorId, assigneeId, admin, filter == null ? TaskFilter.empty() : filter,
                paging(page, size, "id"));
        return new TaskPage(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public TaskView replace(long actorId, long taskId, TaskData data, long version) {
        var project = access.lock(actorId, taskId);
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
        var updated = task.view();
        if (!Objects.equals(previous.assigneeId(), updated.assigneeId()) && updated.assigneeId() != null) {
            outbox.append(OutboxEvents.TASK_ASSIGNED, payload(actorId, updated));
        }
        return updated;
    }

    @Override
    public TaskView execute(long actorId, long taskId, Command command, long version) {
        var project = access.lock(actorId, taskId);
        var task = managed(taskId, version);
        authorizeExecution(actorId, project, task.view(), command == Command.CANCEL);
        try {
            TaskLifecyclePolicy.nextStatus(task.view().status(), command);
        } catch (IllegalStateException invalid) {
            throw error(TaskException.Reason.INVALID_TASK_TRANSITION);
        }
        if (command == Command.START && task.view().bucket() != TaskBucket.WORK) {
            throw error(TaskException.Reason.TASK_NOT_IN_WORK);
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
        access.readable(actorId, taskId);
        var result = attempts.findByTaskId(taskId, paging(page, size, "number"));
        return new AttemptPage(result.getContent().stream().map(AttemptEntity::view).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public void delete(long actorId, long taskId, long version) {
        var project = access.lock(actorId, taskId);
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

    private String payload(long actorId, TaskView task) {
        var body = new LinkedHashMap<String, Object>();
        body.put("actorId", actorId);
        body.put("assigneeId", task.assigneeId());
        body.put("projectId", task.projectId());
        body.put("taskId", task.id());
        body.put("taskName", task.name());
        return json.writeValueAsString(body);
    }

    private static PageRequest paging(int page, int size, String property) {
        return PageRequests.descending(page, size, property, () -> error(TaskException.Reason.VALIDATION_ERROR));
    }

    private static TaskException error(TaskException.Reason reason) {
        return new TaskException(reason);
    }
}

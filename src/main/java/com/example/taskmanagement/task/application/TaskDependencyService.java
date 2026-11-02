package com.example.taskmanagement.task.application;

import com.example.taskmanagement.planning.core.DependencyEdge;
import com.example.taskmanagement.planning.core.GraphValidationException;
import com.example.taskmanagement.planning.core.PriorityTopologicalSorter;
import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.task.api.TaskDependencyOperations;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.domain.TaskStatus;
import com.example.taskmanagement.task.persistence.DependencyRepository;
import com.example.taskmanagement.task.persistence.TaskRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class TaskDependencyService implements TaskDependencyOperations {
    private final TaskProjectAccess access;
    private final TaskRepository tasks;
    private final DependencyRepository dependencies;
    private final TaskGraphLoader loader;
    private final ProjectCoordination coordination;
    private final EntityManager entityManager;
    private final PriorityTopologicalSorter sorter = new PriorityTopologicalSorter();

    public TaskDependencyService(TaskProjectAccess access, TaskRepository tasks, DependencyRepository dependencies,
            TaskGraphLoader loader, ProjectCoordination coordination, EntityManager entityManager) {
        this.access = access;
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.loader = loader;
        this.coordination = coordination;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Predecessors list(long actorId, long taskId) {
        var project = access.readable(actorId, taskId);
        return new Predecessors(taskId, project.id(), project.inputRevision(), dependencies.predecessors(taskId));
    }

    @Override
    public Change add(long actorId, long taskId, long dependsOnTaskId, long inputRevision) {
        long projectId = editable(actorId, taskId, dependsOnTaskId, inputRevision);
        var input = loader.load(actorId, projectId);
        var edge = new DependencyEdge(dependsOnTaskId, taskId);
        if (input.edges().contains(edge)) {
            throw error(TaskException.Reason.DEPENDENCY_ALREADY_EXISTS);
        }
        if (input.edges().size() >= loader.maxDependencies()) {
            throw error(TaskException.Reason.GRAPH_TOO_LARGE);
        }
        var proposed = new ArrayList<>(input.edges());
        proposed.add(edge);
        sorter.executionOrder(new TaskGraphInput(projectId, input.inputRevision(), input.tasks(), proposed).graph());
        dependencies.insert(projectId, taskId, dependsOnTaskId);
        var changed = coordination.markInputChanged(actorId, projectId);
        return new Change(projectId, taskId, dependsOnTaskId, changed.inputRevision());
    }

    @Override
    public Change remove(long actorId, long taskId, long dependsOnTaskId, long inputRevision) {
        long projectId = editable(actorId, taskId, dependsOnTaskId, inputRevision);
        // Removal can repair an oversized/corrupt graph; it does not require loading or sorting that graph.
        if (!dependencies.remove(projectId, taskId, dependsOnTaskId)) {
            throw error(TaskException.Reason.DEPENDENCY_NOT_FOUND);
        }
        var changed = coordination.markInputChanged(actorId, projectId);
        return new Change(projectId, taskId, dependsOnTaskId, changed.inputRevision());
    }

    private long editable(long actorId, long taskId, long dependsOnTaskId, long inputRevision) {
        var project = access.lock(actorId, taskId);
        if (project.inputRevision() != inputRevision) {
            throw error(TaskException.Reason.VERSION_CONFLICT);
        }
        var task = tasks.findById(taskId).orElseThrow(() -> error(TaskException.Reason.TASK_NOT_FOUND));
        entityManager.refresh(task);
        if (task.view().status() != TaskStatus.NOT_STARTED || task.view().attemptCount() != 0) {
            throw error(TaskException.Reason.DEPENDENCY_CHANGE_FORBIDDEN);
        }
        if (taskId == dependsOnTaskId) {
            throw new GraphValidationException(GraphValidationException.Reason.SELF_DEPENDENCY,
                    "Task cannot depend on itself", List.of(taskId));
        }
        if (access.readable(actorId, dependsOnTaskId).id() != project.id()) {
            throw error(TaskException.Reason.CROSS_PROJECT_DEPENDENCY);
        }
        return project.id();
    }

    private static TaskException error(TaskException.Reason reason) {
        return new TaskException(reason);
    }
}

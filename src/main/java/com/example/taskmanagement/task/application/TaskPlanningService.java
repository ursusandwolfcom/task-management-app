package com.example.taskmanagement.task.application;

import com.example.taskmanagement.planning.core.PriorityTopologicalSorter;
import com.example.taskmanagement.planning.core.ReadinessCalculator;
import com.example.taskmanagement.planning.core.TaskNode;
import com.example.taskmanagement.task.api.TaskPlanningOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The loader's transaction ends before algorithms run; no calculation reserves or mutates tasks. */
@Service
@Transactional(propagation = Propagation.NEVER)
public class TaskPlanningService implements TaskPlanningOperations {
    private final TaskGraphLoader loader;
    private final PriorityTopologicalSorter sorter = new PriorityTopologicalSorter();
    private final ReadinessCalculator readiness = new ReadinessCalculator(sorter);

    public TaskPlanningService(TaskGraphLoader loader) {
        this.loader = loader;
    }

    @Override
    public ExecutionOrder executionOrder(long actorId, long projectId) {
        var input = loader.load(actorId, projectId);
        var order = sorter.executionOrder(input.graph()).stream().map(TaskNode::id).toList();
        return new ExecutionOrder(projectId, input.inputRevision(), order);
    }

    @Override
    public AvailableTasks availableTasks(long actorId, long projectId) {
        var input = loader.load(actorId, projectId);
        var graph = input.graph();
        var facts = input.facts();
        var views = input.viewsById();
        var available = readiness.availableTasks(graph, facts).stream().map(node -> views.get(node.id())).toList();
        var blocked = facts.candidateIds().stream().sorted().map(id -> new BlockedTask(id,
                "DEPENDENCIES_NOT_COMPLETED", graph.predecessors(id).stream()
                        .filter(predecessor -> !facts.completedIds().contains(predecessor)).sorted().toList()))
                .filter(reason -> !reason.predecessorIds().isEmpty()).toList();
        return new AvailableTasks(projectId, input.inputRevision(), available, blocked);
    }

    @Override
    public NextTask nextTask(long actorId, long projectId) {
        var input = loader.load(actorId, projectId);
        var views = input.viewsById();
        var next = readiness.nextTask(input.graph(), input.facts()).map(node -> views.get(node.id())).orElse(null);
        return new NextTask(projectId, input.inputRevision(), next);
    }
}

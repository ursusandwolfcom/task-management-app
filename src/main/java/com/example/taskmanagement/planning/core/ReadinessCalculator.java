package com.example.taskmanagement.planning.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Dependency readiness only; actor permissions, assignees and resource capacity belong to application guards. */
public final class ReadinessCalculator {

    private final PriorityTopologicalSorter sorter;

    public ReadinessCalculator(PriorityTopologicalSorter sorter) {
        this.sorter = Objects.requireNonNull(sorter, "sorter");
    }

    /** Rejects cycles anywhere in the graph, even when the candidate set is empty. */
    public List<TaskNode> availableTasks(GraphSnapshot graph, ExecutionFacts facts) {
        return readyCandidates(graph, facts).sorted(TaskOrder.PRIORITY_THEN_DUE_DATE_THEN_ID).toList();
    }

    /** Returns a recommendation without reserving a task or changing execution facts. */
    public Optional<TaskNode> nextTask(GraphSnapshot graph, ExecutionFacts facts) {
        return readyCandidates(graph, facts).min(TaskOrder.PRIORITY_THEN_DUE_DATE_THEN_ID);
    }

    private Stream<TaskNode> readyCandidates(GraphSnapshot graph, ExecutionFacts facts) {
        Objects.requireNonNull(facts, "facts");
        sorter.executionOrder(graph);
        facts.validateAgainst(graph);
        return facts.candidateIds().stream().map(id -> graph.nodes().get(id))
                .filter(node -> facts.completedIds().containsAll(graph.predecessors(node.id())));
    }
}

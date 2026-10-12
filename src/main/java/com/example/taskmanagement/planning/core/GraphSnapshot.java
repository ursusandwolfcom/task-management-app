package com.example.taskmanagement.planning.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Structurally validated, immutable graph input. Acyclicity is checked by the topological sorter,
 * allowing cyclic inputs to be represented for diagnostics without mutating domain objects.
 */
public final class GraphSnapshot {

    private final Map<Long, TaskNode> nodes;
    private final Set<DependencyEdge> edges;
    private final Map<Long, Set<Long>> successors;
    private final Map<Long, Set<Long>> predecessors;

    private GraphSnapshot(Map<Long, TaskNode> nodes, Set<DependencyEdge> edges,
            Map<Long, Set<Long>> successors, Map<Long, Set<Long>> predecessors) {
        this.nodes = Map.copyOf(nodes);
        this.edges = Set.copyOf(edges);
        this.successors = immutableAdjacency(successors);
        this.predecessors = immutableAdjacency(predecessors);
    }

    public static GraphSnapshot of(Collection<TaskNode> tasks, Collection<DependencyEdge> dependencies) {
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(dependencies, "dependencies");
        var nodes = new HashMap<Long, TaskNode>();
        var successors = new HashMap<Long, Set<Long>>();
        var predecessors = new HashMap<Long, Set<Long>>();
        for (var node : tasks) {
            Objects.requireNonNull(node, "task");
            if (nodes.putIfAbsent(node.id(), node) != null) {
                throw new GraphValidationException(GraphValidationException.Reason.DUPLICATE_TASK,
                        "Duplicate task ID", List.of(node.id()));
            }
            successors.put(node.id(), new HashSet<>());
            predecessors.put(node.id(), new HashSet<>());
        }
        var edges = new HashSet<DependencyEdge>();
        for (var edge : dependencies) {
            Objects.requireNonNull(edge, "dependency");
            validateEdge(edge, nodes, edges);
            successors.get(edge.predecessorId()).add(edge.successorId());
            predecessors.get(edge.successorId()).add(edge.predecessorId());
        }
        return new GraphSnapshot(nodes, edges, successors, predecessors);
    }

    public Map<Long, TaskNode> nodes() {
        return nodes;
    }

    public Set<DependencyEdge> edges() {
        return edges;
    }

    public Set<Long> successors(long taskId) {
        return adjacent(successors, taskId);
    }

    public Set<Long> predecessors(long taskId) {
        return adjacent(predecessors, taskId);
    }

    private static void validateEdge(DependencyEdge edge, Map<Long, TaskNode> nodes, Set<DependencyEdge> edges) {
        var missing = List.of(edge.predecessorId(), edge.successorId()).stream()
                .filter(id -> !nodes.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new GraphValidationException(GraphValidationException.Reason.UNKNOWN_TASK,
                    "Dependency references an unknown task", missing);
        }
        if (edge.predecessorId() == edge.successorId()) {
            throw new GraphValidationException(GraphValidationException.Reason.SELF_DEPENDENCY,
                    "Task cannot depend on itself", List.of(edge.predecessorId()));
        }
        if (!edges.add(edge)) {
            throw new GraphValidationException(GraphValidationException.Reason.DUPLICATE_DEPENDENCY,
                    "Duplicate dependency", List.of(edge.predecessorId(), edge.successorId()));
        }
    }

    private static Map<Long, Set<Long>> immutableAdjacency(Map<Long, Set<Long>> adjacency) {
        var copy = new HashMap<Long, Set<Long>>();
        adjacency.forEach((id, neighbors) -> copy.put(id, Set.copyOf(neighbors)));
        return Map.copyOf(copy);
    }

    private static Set<Long> adjacent(Map<Long, Set<Long>> adjacency, long taskId) {
        var neighbors = adjacency.get(taskId);
        if (neighbors == null) {
            throw new GraphValidationException(GraphValidationException.Reason.UNKNOWN_TASK,
                    "Unknown task ID", List.of(taskId));
        }
        return neighbors;
    }
}

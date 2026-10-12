package com.example.taskmanagement.planning.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/** Stateless Kahn algorithm: O(E + V log V) time and O(V + E) graph storage. */
public final class PriorityTopologicalSorter {

    /** Returns all vertices in dependency order; processing a vertex does not mark it completed. */
    public List<TaskNode> executionOrder(GraphSnapshot graph) {
        Objects.requireNonNull(graph, "graph");
        var remainingDegrees = new HashMap<Long, Integer>();
        var ready = new PriorityQueue<>(TaskOrder.PRIORITY_THEN_DUE_DATE_THEN_ID);
        for (var node : graph.nodes().values()) {
            int degree = graph.predecessors(node.id()).size();
            remainingDegrees.put(node.id(), degree);
            if (degree == 0) {
                ready.add(node);
            }
        }
        var order = new ArrayList<TaskNode>(graph.nodes().size());
        while (!ready.isEmpty()) {
            var node = ready.poll();
            order.add(node);
            for (long successorId : graph.successors(node.id())) {
                int degree = remainingDegrees.merge(successorId, -1, Integer::sum);
                if (degree == 0) {
                    ready.add(graph.nodes().get(successorId));
                }
            }
        }
        if (order.size() != graph.nodes().size()) {
            var unresolved = remainingDegrees.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0).map(entry -> entry.getKey()).toList();
            throw new GraphValidationException(GraphValidationException.Reason.CYCLIC_DEPENDENCY,
                    "Graph contains a cycle; unresolved IDs may also include its descendants", unresolved);
        }
        return List.copyOf(order);
    }
}

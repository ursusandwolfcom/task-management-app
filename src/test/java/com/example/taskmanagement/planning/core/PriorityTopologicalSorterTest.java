package com.example.taskmanagement.planning.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class PriorityTopologicalSorterTest {

    private final PriorityTopologicalSorter sorter = new PriorityTopologicalSorter();

    @Test
    void sortsEmptyAndSingleVertexGraphs() {
        assertEquals(List.of(), sorter.executionOrder(GraphSnapshot.of(List.of(), List.of())));
        var task = node(1, 10);
        assertEquals(List.of(task), sorter.executionOrder(GraphSnapshot.of(List.of(task), List.of())));
    }

    @Test
    void dependencyWinsOverSuccessorPriority() {
        var graph = GraphSnapshot.of(List.of(node(1, 0), node(2, 100)), List.of(new DependencyEdge(1, 2)));
        assertEquals(List.of(1L, 2L), ids(sorter.executionOrder(graph)));
    }

    @Test
    void newlyUnlockedHighPriorityTaskWinsOverOlderReadyTask() {
        var graph = GraphSnapshot.of(List.of(node(1, 20), node(2, 30), node(3, 10)),
                List.of(new DependencyEdge(1, 2)));
        assertEquals(List.of(1L, 2L, 3L), ids(sorter.executionOrder(graph)));
    }

    @Test
    void sortsDiamondAndDisconnectedComponents() {
        var graph = GraphSnapshot.of(List.of(node(1, 10), node(2, 20), node(3, 30), node(4, 100), node(5, 5)),
                List.of(new DependencyEdge(1, 2), new DependencyEdge(1, 3),
                        new DependencyEdge(2, 4), new DependencyEdge(3, 4)));
        assertEquals(List.of(1L, 3L, 2L, 4L, 5L), ids(sorter.executionOrder(graph)));
    }

    @Test
    void resolvesTiesByDueDateThenIdWithNullDeadlineLast() {
        var date = LocalDate.of(2026, 10, 12);
        var graph = GraphSnapshot.of(List.of(
                new TaskNode(4, 10, null, null), new TaskNode(3, 10, date.plusDays(1), null),
                new TaskNode(2, 10, date, null), new TaskNode(1, 10, date, null)), List.of());
        assertEquals(List.of(1L, 2L, 3L, 4L), ids(sorter.executionOrder(graph)));
    }

    @Test
    void priorityWinsOverAnEarlierDeadline() {
        var graph = GraphSnapshot.of(List.of(new TaskNode(1, 10, LocalDate.MIN, null),
                new TaskNode(2, 20, null, null)), List.of());
        assertEquals(List.of(2L, 1L), ids(sorter.executionOrder(graph)));
    }

    @Test
    void comparesLargeIdsAndWeightsWithoutOverflow() {
        var graph = GraphSnapshot.of(List.of(node(Long.MAX_VALUE, 0), node(1, 0),
                node(2, Integer.MAX_VALUE)), List.of());
        assertEquals(List.of(2L, 1L, Long.MAX_VALUE), ids(sorter.executionOrder(graph)));
    }

    @Test
    void reportsStableUnresolvedIdsIncludingCycleDescendants() {
        var graph = GraphSnapshot.of(List.of(node(1, 0), node(3, 10), node(4, 10), node(5, 10)),
                List.of(new DependencyEdge(3, 4), new DependencyEdge(4, 3), new DependencyEdge(4, 5)));
        var error = assertThrows(GraphValidationException.class, () -> sorter.executionOrder(graph));
        assertEquals(GraphValidationException.Reason.CYCLIC_DEPENDENCY, error.reason());
        assertEquals(List.of(3L, 4L, 5L), error.taskIds());
        assertThrows(UnsupportedOperationException.class, () -> error.taskIds().clear());
    }

    @Test
    void inputOrderDoesNotAffectResult() {
        var nodes = new ArrayList<>(List.of(node(1, 10), node(2, 20), node(3, 20), node(4, 0)));
        var edges = new ArrayList<>(List.of(new DependencyEdge(1, 2), new DependencyEdge(1, 3)));
        var expected = List.of(1L, 2L, 3L, 4L);
        var random = new Random(17);
        for (int attempt = 0; attempt < 30; attempt++) {
            Collections.shuffle(nodes, random);
            Collections.shuffle(edges, random);
            assertEquals(expected, ids(sorter.executionOrder(GraphSnapshot.of(nodes, edges))));
        }
    }

    @Test
    void everyEdgePrecedesItsSuccessorInGeneratedDags() {
        var random = new Random(23);
        for (int attempt = 0; attempt < 50; attempt++) {
            var nodes = new ArrayList<TaskNode>();
            var edges = new ArrayList<DependencyEdge>();
            for (long id = 1; id <= 40; id++) {
                nodes.add(node(id, random.nextInt(4)));
                for (long predecessor = 1; predecessor < id; predecessor++) {
                    if (random.nextInt(10) == 0) {
                        edges.add(new DependencyEdge(predecessor, id));
                    }
                }
            }
            Collections.shuffle(nodes, random);
            Collections.shuffle(edges, random);
            var order = sorter.executionOrder(GraphSnapshot.of(nodes, edges));
            var positions = new HashMap<Long, Integer>();
            for (int index = 0; index < order.size(); index++) {
                assertEquals(null, positions.put(order.get(index).id(), index));
            }
            assertEquals(nodes.size(), positions.size());
            for (var edge : edges) {
                assertTrue(positions.get(edge.predecessorId()) < positions.get(edge.successorId()));
            }
        }
    }

    @Test
    void handlesLongChainWithoutRecursiveTraversal() {
        var nodes = new ArrayList<TaskNode>();
        var edges = new ArrayList<DependencyEdge>();
        for (long id = 1; id <= 10000; id++) {
            nodes.add(node(id, 10));
            if (id > 1) {
                edges.add(new DependencyEdge(id - 1, id));
            }
        }
        var order = sorter.executionOrder(GraphSnapshot.of(nodes, edges));
        assertEquals(10000, order.size());
        assertEquals(1, order.getFirst().id());
        assertEquals(10000, order.getLast().id());
    }

    @Test
    void repeatedAndConcurrentCallsDoNotShareMutableState() throws Exception {
        var graph = GraphSnapshot.of(List.of(node(1, 0), node(2, 100)), List.of(new DependencyEdge(1, 2)));
        var expected = List.of(1L, 2L);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var calls = new ArrayList<Callable<List<Long>>>();
            for (int index = 0; index < 20; index++) {
                calls.add(() -> ids(sorter.executionOrder(graph)));
            }
            for (var result : executor.invokeAll(calls)) {
                assertEquals(expected, result.get());
            }
        }
        assertEquals(expected, ids(sorter.executionOrder(graph)));
        assertEquals(1, graph.predecessors(2).size());
        assertThrows(UnsupportedOperationException.class, () -> sorter.executionOrder(graph).clear());
    }

    private static TaskNode node(long id, int weight) {
        return new TaskNode(id, weight, null, null);
    }

    private static List<Long> ids(List<TaskNode> nodes) {
        return nodes.stream().map(TaskNode::id).toList();
    }
}

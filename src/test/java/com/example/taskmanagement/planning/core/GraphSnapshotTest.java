package com.example.taskmanagement.planning.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphSnapshotTest {

    @Test
    void acceptsEmptyGraphAndMissingEstimate() {
        assertEquals(0, GraphSnapshot.of(List.of(), List.of()).nodes().size());
        assertEquals(1, GraphSnapshot.of(List.of(node(1)), List.of()).nodes().size());
    }

    @Test
    void rejectsDuplicateTaskIds() {
        var error = assertThrows(GraphValidationException.class,
                () -> GraphSnapshot.of(List.of(node(1), node(1)), List.of()));
        assertEquals(GraphValidationException.Reason.DUPLICATE_TASK, error.reason());
        assertEquals(List.of(1L), error.taskIds());
    }

    @Test
    void rejectsUnknownDependencyEndpoints() {
        var error = assertThrows(GraphValidationException.class,
                () -> GraphSnapshot.of(List.of(node(1)), List.of(new DependencyEdge(2, 3))));
        assertEquals(GraphValidationException.Reason.UNKNOWN_TASK, error.reason());
        assertEquals(List.of(2L, 3L), error.taskIds());
    }

    @Test
    void rejectsSelfDependency() {
        var error = assertThrows(GraphValidationException.class,
                () -> GraphSnapshot.of(List.of(node(1)), List.of(new DependencyEdge(1, 1))));
        assertEquals(GraphValidationException.Reason.SELF_DEPENDENCY, error.reason());
    }

    @Test
    void rejectsDuplicateDependencies() {
        var edge = new DependencyEdge(1, 2);
        var error = assertThrows(GraphValidationException.class,
                () -> GraphSnapshot.of(List.of(node(1), node(2)), List.of(edge, edge)));
        assertEquals(GraphValidationException.Reason.DUPLICATE_DEPENDENCY, error.reason());
    }

    @Test
    void makesDefensiveCopiesOfInputsAndAllExposedCollections() {
        var nodes = new ArrayList<>(List.of(node(1), node(2)));
        var edges = new ArrayList<>(List.of(new DependencyEdge(1, 2)));
        var graph = GraphSnapshot.of(nodes, edges);
        nodes.clear();
        edges.clear();

        assertEquals(Set.of(1L, 2L), graph.nodes().keySet());
        assertEquals(Set.of(2L), graph.successors(1));
        assertEquals(Set.of(1L), graph.predecessors(2));
        assertEquals(Set.of(new DependencyEdge(1, 2)), graph.edges());
        assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.successors(1).clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.predecessors(2).clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.edges().clear());
    }

    @Test
    void rejectsUnknownVertexWhenReadingAdjacency() {
        var graph = GraphSnapshot.of(List.of(node(1)), List.of());
        assertThrows(GraphValidationException.class, () -> graph.successors(2));
        assertThrows(GraphValidationException.class, () -> graph.predecessors(2));
    }

    @Test
    void rejectsInvalidIdsAndPriorityWeights() {
        assertThrows(IllegalArgumentException.class, () -> node(0));
        assertThrows(IllegalArgumentException.class, () -> node(-1));
        assertThrows(IllegalArgumentException.class, () -> new TaskNode(1, -1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DependencyEdge(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new DependencyEdge(1, -1));
    }

    @Test
    void rejectsNullCollectionsAndElements() {
        assertThrows(NullPointerException.class, () -> GraphSnapshot.of(null, List.of()));
        assertThrows(NullPointerException.class, () -> GraphSnapshot.of(List.of(), null));
        var nodes = new ArrayList<TaskNode>();
        nodes.add(null);
        assertThrows(NullPointerException.class, () -> GraphSnapshot.of(nodes, List.of()));
        var edges = new ArrayList<DependencyEdge>();
        edges.add(null);
        assertThrows(NullPointerException.class, () -> GraphSnapshot.of(List.of(node(1)), edges));
    }

    private static TaskNode node(long id) {
        return new TaskNode(id, 10, null, null);
    }

    @Test
    void acceptsMinutePrecisionAndRejectsNonpositiveEstimates() {
        assertEquals(30L, new TaskNode(1, 10, null, 30L).estimatedMinutes());
        assertEquals(1L, new TaskNode(1, 10, null, 1L).estimatedMinutes());
        assertThrows(IllegalArgumentException.class, () -> new TaskNode(1, 10, null, 0L));
        assertThrows(IllegalArgumentException.class, () -> new TaskNode(1, 10, null, -1L));
    }
}

package com.example.taskmanagement.planning.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReadinessCalculatorTest {

    private final PriorityTopologicalSorter sorter = new PriorityTopologicalSorter();
    private final ReadinessCalculator calculator = new ReadinessCalculator(sorter);

    @Test
    void emptyGraphHasNoAvailableOrNextTask() {
        var graph = GraphSnapshot.of(List.of(), List.of());
        var facts = new ExecutionFacts(Set.of(), Set.of());
        assertEquals(List.of(), calculator.availableTasks(graph, facts));
        assertTrue(calculator.nextTask(graph, facts).isEmpty());
    }

    @Test
    void executionOrderDoesNotVirtuallyCompletePredecessor() {
        var graph = chain();
        var facts = new ExecutionFacts(Set.of(), Set.of(1L, 2L));
        assertEquals(List.of(1L, 2L), ids(sorter.executionOrder(graph)));
        assertEquals(List.of(1L), ids(calculator.availableTasks(graph, facts)));
        assertEquals(1L, calculator.nextTask(graph, facts).orElseThrow().id());
        assertEquals(Set.of(), facts.completedIds());
        assertEquals(Set.of(1L, 2L), facts.candidateIds());
    }

    @Test
    void inProgressOrFailedPredecessorKeepsSuccessorBlocked() {
        var facts = new ExecutionFacts(Set.of(), Set.of(2L));
        assertEquals(List.of(), calculator.availableTasks(chain(), facts));
        assertTrue(calculator.nextTask(chain(), facts).isEmpty());
    }

    @Test
    void actualCompletionUnlocksSuccessor() {
        var facts = new ExecutionFacts(Set.of(1L), Set.of(2L));
        assertEquals(List.of(2L), ids(calculator.availableTasks(chain(), facts)));
        assertEquals(2L, calculator.nextTask(chain(), facts).orElseThrow().id());
    }

    @Test
    void requiresEveryPredecessorToBeCompleted() {
        var graph = GraphSnapshot.of(List.of(node(1, 10), node(2, 10), node(3, 30)),
                List.of(new DependencyEdge(1, 3), new DependencyEdge(2, 3)));
        assertEquals(List.of(), calculator.availableTasks(graph, new ExecutionFacts(Set.of(1L), Set.of(3L))));
        assertEquals(List.of(3L), ids(calculator.availableTasks(graph,
                new ExecutionFacts(Set.of(1L, 2L), Set.of(3L)))));
    }

    @Test
    void availableIsSortedByActualReadinessRatherThanFilteringVirtualExecutionOrder() {
        var graph = GraphSnapshot.of(List.of(node(1, 0), node(2, 100), node(3, 50)),
                List.of(new DependencyEdge(1, 2)));
        var facts = new ExecutionFacts(Set.of(1L), Set.of(2L, 3L));
        assertEquals(List.of(3L, 1L, 2L), ids(sorter.executionOrder(graph)));
        assertEquals(List.of(2L, 3L), ids(calculator.availableTasks(graph, facts)));
        assertEquals(2L, calculator.nextTask(graph, facts).orElseThrow().id());
    }

    @Test
    void availableUsesDeadlineAndIdTiesAndExcludesNonCandidates() {
        var deadline = LocalDate.of(2026, 10, 12);
        var graph = GraphSnapshot.of(List.of(node(1, 100), new TaskNode(2, 10, null, null),
                new TaskNode(4, 10, deadline, 30L), new TaskNode(3, 10, deadline, 1L)), List.of());
        var facts = new ExecutionFacts(Set.of(), Set.of(2L, 3L, 4L));
        assertEquals(List.of(3L, 4L, 2L), ids(calculator.availableTasks(graph, facts)));
        assertFalse(ids(calculator.availableTasks(graph, facts)).contains(1L));
        assertThrows(UnsupportedOperationException.class, () -> calculator.availableTasks(graph, facts).clear());
    }

    @Test
    void rejectsCycleEvenWhenNoCandidatesWouldBeReturned() {
        var graph = GraphSnapshot.of(List.of(node(1, 10), node(2, 10)),
                List.of(new DependencyEdge(1, 2), new DependencyEdge(2, 1)));
        var error = assertThrows(GraphValidationException.class,
                () -> calculator.availableTasks(graph, new ExecutionFacts(Set.of(), Set.of())));
        assertEquals(GraphValidationException.Reason.CYCLIC_DEPENDENCY, error.reason());
    }

    @Test
    void rejectsUnknownCompletedAndCandidateIds() {
        for (var facts : List.of(new ExecutionFacts(Set.of(3L), Set.of()),
                new ExecutionFacts(Set.of(), Set.of(3L)))) {
            var error = assertThrows(GraphValidationException.class, () -> calculator.availableTasks(chain(), facts));
            assertEquals(GraphValidationException.Reason.UNKNOWN_TASK, error.reason());
            assertEquals(List.of(3L), error.taskIds());
        }
    }

    @Test
    void rejectsOverlappingOrInvalidFacts() {
        var error = assertThrows(GraphValidationException.class,
                () -> new ExecutionFacts(Set.of(1L), Set.of(1L)));
        assertEquals(GraphValidationException.Reason.INVALID_EXECUTION_FACTS, error.reason());
        assertThrows(IllegalArgumentException.class, () -> new ExecutionFacts(Set.of(0L), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionFacts(Set.of(), Set.of(-1L)));
        assertThrows(NullPointerException.class, () -> new ExecutionFacts(null, Set.of()));
        assertThrows(NullPointerException.class, () -> new ExecutionFacts(Set.of(), null));
        var nullIds = new HashSet<Long>();
        nullIds.add(null);
        assertThrows(NullPointerException.class, () -> new ExecutionFacts(nullIds, Set.of()));
    }

    @Test
    void factsAreDefensivelyCopiedAndResultsAreRepeatable() {
        var completed = new HashSet<>(Set.of(1L));
        var candidates = new HashSet<>(Set.of(2L));
        var facts = new ExecutionFacts(completed, candidates);
        completed.clear();
        candidates.clear();
        assertEquals(Set.of(1L), facts.completedIds());
        assertEquals(Set.of(2L), facts.candidateIds());
        assertThrows(UnsupportedOperationException.class, () -> facts.completedIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> facts.candidateIds().clear());
        for (int attempt = 0; attempt < 10; attempt++) {
            assertEquals(List.of(2L), ids(calculator.availableTasks(chain(), facts)));
        }
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> new ReadinessCalculator(null));
        assertThrows(NullPointerException.class, () -> calculator.availableTasks(null,
                new ExecutionFacts(Set.of(), Set.of())));
        assertThrows(NullPointerException.class, () -> calculator.availableTasks(chain(), null));
    }

    private static GraphSnapshot chain() {
        return GraphSnapshot.of(List.of(node(1, 0), node(2, 100)), List.of(new DependencyEdge(1, 2)));
    }

    private static TaskNode node(long id, int weight) {
        return new TaskNode(id, weight, null, null);
    }

    private static List<Long> ids(List<TaskNode> tasks) {
        return tasks.stream().map(TaskNode::id).toList();
    }
}

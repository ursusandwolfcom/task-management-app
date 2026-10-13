package com.example.taskmanagement.planning.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Independent exhaustive oracle rather than another implementation of Kahn's algorithm. */
class PlanningOracleTest {

    private static final Comparator<TaskNode> PREFERENCE = Comparator.comparingInt(TaskNode::priorityWeight)
            .reversed().thenComparing(TaskNode::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingLong(TaskNode::id);

    @Test
    void priorityOrderMatchesBestOfEveryValidPermutationAcrossSeededDags() {
        var random = new Random(20261013L);
        var sorter = new PriorityTopologicalSorter();
        for (int sample = 0; sample < 80; sample++) {
            var nodes = nodes(6, random);
            var edges = edges(nodes, random);
            var permutations = new ArrayList<List<TaskNode>>();
            enumerate(nodes, new ArrayList<>(), permutations);
            var expected = permutations.stream().filter(order -> obeysEdges(order, edges))
                    .min(PlanningOracleTest::compareOrders).orElseThrow();
            Collections.shuffle(nodes, random);
            Collections.shuffle(edges, random);
            var graph = GraphSnapshot.of(nodes, edges);
            assertEquals(expected, sorter.executionOrder(graph), "Seeded DAG sample " + sample);
            assertEquals(new HashSet<>(edges), graph.edges());
        }
    }

    @Test
    void readinessMatchesActualCompletedPredecessorsAcrossSeededExecutionFacts() {
        var random = new Random(20261014L);
        var calculator = new ReadinessCalculator(new PriorityTopologicalSorter());
        for (int sample = 0; sample < 250; sample++) {
            var nodes = nodes(12, random);
            var edges = edges(nodes, random);
            var completed = new HashSet<Long>();
            var candidates = new HashSet<Long>();
            for (var node : nodes) {
                int state = random.nextInt(3);
                if (state == 0) {
                    completed.add(node.id());
                } else if (state == 1) {
                    candidates.add(node.id());
                }
            }
            var expected = nodes.stream().filter(node -> candidates.contains(node.id()))
                    .filter(node -> edges.stream().filter(edge -> edge.successorId() == node.id())
                            .allMatch(edge -> completed.contains(edge.predecessorId())))
                    .sorted(PREFERENCE).toList();
            var facts = new ExecutionFacts(completed, candidates);
            var graph = GraphSnapshot.of(nodes, edges);
            assertEquals(expected, calculator.availableTasks(graph, facts), "Execution sample " + sample);
            assertEquals(expected.stream().findFirst(), calculator.nextTask(graph, facts));
            assertEquals(completed, facts.completedIds());
            assertEquals(candidates, facts.candidateIds());
        }
    }

    private static ArrayList<TaskNode> nodes(int size, Random random) {
        var nodes = new ArrayList<TaskNode>();
        int[] weights = {0, 10, 20, Integer.MAX_VALUE};
        for (int index = 0; index < size; index++) {
            var dueDate = random.nextBoolean() ? null : LocalDate.of(2026, 10, 13).plusDays(random.nextInt(4));
            nodes.add(new TaskNode((size - index) * 7L, weights[random.nextInt(weights.length)], dueDate, 1L));
        }
        return nodes;
    }

    private static ArrayList<DependencyEdge> edges(List<TaskNode> nodes, Random random) {
        var edges = new ArrayList<DependencyEdge>();
        for (int before = 0; before < nodes.size(); before++) {
            for (int after = before + 1; after < nodes.size(); after++) {
                if (random.nextInt(4) == 0) {
                    edges.add(new DependencyEdge(nodes.get(before).id(), nodes.get(after).id()));
                }
            }
        }
        return edges;
    }

    private static void enumerate(List<TaskNode> remaining, List<TaskNode> prefix, List<List<TaskNode>> output) {
        if (remaining.isEmpty()) {
            output.add(List.copyOf(prefix));
            return;
        }
        for (var node : remaining) {
            var rest = new ArrayList<>(remaining);
            rest.remove(node);
            prefix.add(node);
            enumerate(rest, prefix, output);
            prefix.removeLast();
        }
    }

    private static boolean obeysEdges(List<TaskNode> order, List<DependencyEdge> edges) {
        var positions = new HashMap<Long, Integer>();
        for (int index = 0; index < order.size(); index++) {
            positions.put(order.get(index).id(), index);
        }
        return edges.stream().allMatch(edge -> positions.get(edge.predecessorId()) < positions.get(edge.successorId()));
    }

    private static int compareOrders(List<TaskNode> left, List<TaskNode> right) {
        for (int index = 0; index < left.size(); index++) {
            int preference = PREFERENCE.compare(left.get(index), right.get(index));
            if (preference != 0) {
                return preference;
            }
        }
        return 0;
    }
}

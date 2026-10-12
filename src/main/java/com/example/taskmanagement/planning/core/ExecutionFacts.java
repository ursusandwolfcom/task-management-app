package com.example.taskmanagement.planning.core;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/** The task adapter supplies completed tasks and eligible not-started candidates, without a core status enum. */
public record ExecutionFacts(Set<Long> completedIds, Set<Long> candidateIds) {

    public ExecutionFacts {
        completedIds = Set.copyOf(completedIds);
        candidateIds = Set.copyOf(candidateIds);
        if (Stream.concat(completedIds.stream(), candidateIds.stream()).anyMatch(id -> id <= 0)) {
            throw new IllegalArgumentException("Execution fact IDs must be positive");
        }
        var overlapping = new HashSet<>(completedIds);
        overlapping.retainAll(candidateIds);
        if (!overlapping.isEmpty()) {
            throw new GraphValidationException(GraphValidationException.Reason.INVALID_EXECUTION_FACTS,
                    "A task cannot be both completed and a candidate", overlapping);
        }
    }

    void validateAgainst(GraphSnapshot graph) {
        var unknown = Stream.concat(completedIds.stream(), candidateIds.stream())
                .filter(id -> !graph.nodes().containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new GraphValidationException(GraphValidationException.Reason.UNKNOWN_TASK,
                    "Execution facts reference an unknown task", unknown);
        }
    }
}

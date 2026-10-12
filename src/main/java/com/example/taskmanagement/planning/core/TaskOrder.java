package com.example.taskmanagement.planning.core;

import java.util.Comparator;

/** Shared deterministic selection policy for topological order and actual readiness. */
public final class TaskOrder {

    public static final Comparator<TaskNode> PRIORITY_THEN_DUE_DATE_THEN_ID =
            Comparator.comparingInt(TaskNode::priorityWeight).reversed()
                    .thenComparing(TaskNode::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingLong(TaskNode::id);

    private TaskOrder() {
    }
}

package com.example.taskmanagement.planning.core;

import java.time.LocalDate;

/** Immutable algorithm input. Priority weights are mapped by the task adapter, not by enum ordinal. */
public record TaskNode(long id, int priorityWeight, LocalDate dueDate, Long estimatedMinutes) {

    public TaskNode {
        if (id <= 0) {
            throw new IllegalArgumentException("Task ID must be positive");
        }
        if (priorityWeight < 0) {
            throw new IllegalArgumentException("Priority weight must be nonnegative");
        }
        if (estimatedMinutes != null && estimatedMinutes <= 0) {
            throw new IllegalArgumentException("Estimated effort must be positive whole minutes when supplied");
        }
    }
}

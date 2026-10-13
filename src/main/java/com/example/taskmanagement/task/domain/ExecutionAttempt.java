package com.example.taskmanagement.task.domain;

import java.time.Instant;
import java.util.Objects;

/** One actual start and its optional terminal outcome, using UTC instants. */
public record ExecutionAttempt(int number, Instant actualStart, Instant actualEnd, TaskStatus outcome) {

    public ExecutionAttempt {
        if (number <= 0) {
            throw new IllegalArgumentException("Attempt number must be positive");
        }
        Objects.requireNonNull(actualStart, "actualStart");
        if ((actualEnd == null) != (outcome == null)) {
            throw new IllegalArgumentException("End and outcome must both be present or absent");
        }
        if (actualEnd != null && actualEnd.isBefore(actualStart)) {
            throw new IllegalArgumentException("Attempt end cannot precede its start");
        }
        if (outcome != null && outcome != TaskStatus.COMPLETED && outcome != TaskStatus.FAILED
                && outcome != TaskStatus.CANCELLED) {
            throw new IllegalArgumentException("Attempt outcome must be completed, failed or cancelled");
        }
    }

    public ExecutionAttempt finish(Instant at, TaskStatus result) {
        if (outcome != null) {
            throw new IllegalStateException("A finished attempt cannot be changed");
        }
        return new ExecutionAttempt(number, actualStart, Objects.requireNonNull(at, "at"),
                Objects.requireNonNull(result, "result"));
    }
}

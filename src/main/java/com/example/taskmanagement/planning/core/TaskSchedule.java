package com.example.taskmanagement.planning.core;

import java.time.Instant;

/** Planned working-time window for one task. Float is lateStart − earlyStart in working minutes. */
public record TaskSchedule(
        long taskId,
        long durationMinutes,
        Instant earlyStart,
        Instant earlyFinish,
        Instant lateStart,
        Instant lateFinish,
        long floatMinutes,
        boolean critical) {

    public TaskSchedule {
        if (taskId <= 0) {
            throw new IllegalArgumentException("Task ID must be positive");
        }
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("Duration must be nonnegative");
        }
        if (floatMinutes < 0) {
            throw new IllegalArgumentException("Float must be nonnegative");
        }
        if (earlyStart == null || earlyFinish == null || lateStart == null || lateFinish == null) {
            throw new IllegalArgumentException("Schedule instants are required");
        }
        if (earlyFinish.isBefore(earlyStart) || lateFinish.isBefore(lateStart)) {
            throw new IllegalArgumentException("Finish is before start");
        }
    }
}

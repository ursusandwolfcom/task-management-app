package com.example.taskmanagement.planning.core;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Forward/backward CPM over working time. Multiple zero-float paths are retained. */
public record ProjectSchedule(
        Instant projectStart,
        Instant projectFinish,
        long durationMinutes,
        List<TaskSchedule> tasks,
        List<Long> criticalTaskIds,
        List<DependencyEdge> criticalEdges) {

    public ProjectSchedule {
        Objects.requireNonNull(projectStart, "projectStart");
        Objects.requireNonNull(projectFinish, "projectFinish");
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("Duration must be nonnegative");
        }
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        criticalTaskIds = List.copyOf(Objects.requireNonNull(criticalTaskIds, "criticalTaskIds"));
        criticalEdges = List.copyOf(Objects.requireNonNull(criticalEdges, "criticalEdges"));
    }
}

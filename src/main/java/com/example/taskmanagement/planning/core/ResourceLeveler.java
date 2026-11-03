package com.example.taskmanagement.planning.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hard leveling (projection): serialize overlapping [earlyStart, earlyFinish) for the same
 * assignee by shifting early starts forward in working time. Tasks without assignee are ignored.
 * Late dates are kept; float is recomputed. Does not mutate persisted tasks.
 */
public final class ResourceLeveler {
    private ResourceLeveler() {
    }

    public static List<TaskSchedule> level(List<TaskSchedule> tasks, Map<Long, Long> assigneeByTaskId,
            WorkingCalendar calendar) {
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(assigneeByTaskId, "assigneeByTaskId");
        Objects.requireNonNull(calendar, "calendar");
        Map<Long, TaskSchedule> byId = new HashMap<>();
        for (TaskSchedule task : tasks) {
            byId.put(task.taskId(), task);
        }
        Map<Long, List<TaskSchedule>> byAssignee = new HashMap<>();
        for (TaskSchedule task : tasks) {
            Long assignee = assigneeByTaskId.get(task.taskId());
            if (assignee == null || task.durationMinutes() == 0L
                    || !task.earlyStart().isBefore(task.earlyFinish())) {
                continue;
            }
            byAssignee.computeIfAbsent(assignee, ignored -> new ArrayList<>()).add(task);
        }
        for (List<TaskSchedule> group : byAssignee.values()) {
            group.sort(Comparator.comparing(TaskSchedule::earlyStart).thenComparingLong(TaskSchedule::taskId));
            Instant cursor = null;
            for (TaskSchedule task : group) {
                Instant start = task.earlyStart();
                if (cursor != null && start.isBefore(cursor)) {
                    start = calendar.normalizeStart(cursor);
                }
                Instant finish = calendar.addWorkingMinutes(start, task.durationMinutes());
                long floatMinutes = task.lateStart().isBefore(start) ? 0L
                        : calendar.workingMinutesBetween(start, task.lateStart());
                byId.put(task.taskId(), new TaskSchedule(task.taskId(), task.durationMinutes(), start, finish,
                        task.lateStart(), task.lateFinish(), floatMinutes, floatMinutes == 0L,
                        task.deadlineOverrun()));
                cursor = finish;
            }
        }
        List<TaskSchedule> leveled = new ArrayList<>(tasks.size());
        for (TaskSchedule task : tasks) {
            leveled.add(byId.get(task.taskId()));
        }
        return List.copyOf(leveled);
    }
}

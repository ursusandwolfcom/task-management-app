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
 * assignee by shifting early starts forward in working time. Optional per-assignee daily
 * capacity (FTE minutes) and break overrides use a tailored calendar. Tasks without assignee
 * are ignored. Late dates are kept; float is recomputed. Does not mutate persisted tasks.
 */
public final class ResourceLeveler {
    private ResourceLeveler() {
    }

    public static List<TaskSchedule> level(List<TaskSchedule> tasks, Map<Long, Long> assigneeByTaskId,
            WorkingCalendar calendar) {
        return level(tasks, assigneeByTaskId, calendar, Map.of(), Map.of());
    }

    public static List<TaskSchedule> level(List<TaskSchedule> tasks, Map<Long, Long> assigneeByTaskId,
            WorkingCalendar calendar, Map<Long, Integer> dailyCapacityByAssignee) {
        return level(tasks, assigneeByTaskId, calendar, dailyCapacityByAssignee, Map.of());
    }

    public static List<TaskSchedule> level(List<TaskSchedule> tasks, Map<Long, Long> assigneeByTaskId,
            WorkingCalendar calendar, Map<Long, Integer> dailyCapacityByAssignee,
            Map<Long, List<BreakWindow>> breaksByAssignee) {
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(assigneeByTaskId, "assigneeByTaskId");
        Objects.requireNonNull(calendar, "calendar");
        Objects.requireNonNull(dailyCapacityByAssignee, "dailyCapacityByAssignee");
        Objects.requireNonNull(breaksByAssignee, "breaksByAssignee");
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
        for (Map.Entry<Long, List<TaskSchedule>> entry : byAssignee.entrySet()) {
            WorkingCalendar assigneeCalendar = calendarFor(calendar, dailyCapacityByAssignee.get(entry.getKey()),
                    breaksByAssignee.get(entry.getKey()));
            List<TaskSchedule> group = entry.getValue();
            group.sort(Comparator.comparing(TaskSchedule::earlyStart).thenComparingLong(TaskSchedule::taskId));
            Instant cursor = null;
            for (TaskSchedule task : group) {
                Instant start = task.earlyStart();
                if (cursor != null && start.isBefore(cursor)) {
                    start = assigneeCalendar.normalizeStart(cursor);
                }
                Instant finish = assigneeCalendar.addWorkingMinutes(start, task.durationMinutes());
                long floatMinutes = task.lateStart().isBefore(start) ? 0L
                        : assigneeCalendar.workingMinutesBetween(start, task.lateStart());
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

    private static WorkingCalendar calendarFor(WorkingCalendar base, Integer capacity, List<BreakWindow> breaks) {
        WorkingCalendar calendar = breaks == null || breaks.isEmpty()
                ? base
                : new WorkingCalendar(base.zone(), base.holidays(), breaks);
        if (capacity == null) {
            return calendar;
        }
        int capped = Math.min(capacity, calendar.minutesPerDay());
        if (capped == calendar.dailyCapacityMinutes()) {
            return calendar;
        }
        return calendar.withDailyCapacityMinutes(capped);
    }
}

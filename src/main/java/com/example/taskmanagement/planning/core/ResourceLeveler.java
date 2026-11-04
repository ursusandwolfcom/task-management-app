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
 * capacity (FTE minutes) and break overrides use a tailored calendar. Dependency shifts also
 * propagate to unassigned tasks and milestones. Late CPM dates are kept as the unlimited-resource
 * baseline; remaining float is clamped to zero. Does not mutate persisted tasks.
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
        return level(tasks, assigneeByTaskId, calendar, dailyCapacityByAssignee, breaksByAssignee, List.of());
    }

    public static List<TaskSchedule> level(List<TaskSchedule> tasks, Map<Long, Long> assigneeByTaskId,
            WorkingCalendar calendar, Map<Long, Integer> dailyCapacityByAssignee,
            Map<Long, List<BreakWindow>> breaksByAssignee, List<DependencyEdge> dependencies) {
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(assigneeByTaskId, "assigneeByTaskId");
        Objects.requireNonNull(calendar, "calendar");
        Objects.requireNonNull(dailyCapacityByAssignee, "dailyCapacityByAssignee");
        Objects.requireNonNull(breaksByAssignee, "breaksByAssignee");
        Map<Long, TaskSchedule> byId = new HashMap<>();
        for (TaskSchedule task : tasks) {
            byId.put(task.taskId(), task);
        }
        var graph = GraphSnapshot.of(tasks.stream()
                .map(task -> new TaskNode(task.taskId(), 0, null, null)).toList(), dependencies);
        var selection = Comparator.comparing((TaskNode node) -> byId.get(node.id()).earlyStart())
                .thenComparingLong(TaskNode::id);
        var order = new PriorityTopologicalSorter().executionOrder(graph, selection);
        Map<Long, Instant> cursorByAssignee = new HashMap<>();
        Map<Long, WorkingCalendar> calendars = new HashMap<>();
        for (TaskNode node : order) {
            TaskSchedule task = byId.get(node.id());
            Long assignee = assigneeByTaskId.get(node.id());
            WorkingCalendar effective = assignee == null ? calendar : calendars.computeIfAbsent(assignee,
                    id -> calendarFor(calendar, dailyCapacityByAssignee.get(id), breaksByAssignee.get(id)));
            Instant start = task.earlyStart();
            for (long predecessor : graph.predecessors(node.id())) {
                Instant finish = byId.get(predecessor).earlyFinish();
                if (start.isBefore(finish)) {
                    start = finish;
                }
            }
            if (assignee != null && task.durationMinutes() > 0L) {
                Instant cursor = cursorByAssignee.get(assignee);
                if (cursor != null && start.isBefore(cursor)) {
                    start = cursor;
                }
            }
            if (task.durationMinutes() > 0L) {
                start = effective.normalizeStart(start);
            }
            Instant finish = task.durationMinutes() == 0L ? start
                    : effective.addWorkingMinutes(start, task.durationMinutes());
            long floatMinutes = task.lateStart().isBefore(start) ? 0L
                    : effective.workingMinutesBetween(start, task.lateStart());
            byId.put(node.id(), new TaskSchedule(node.id(), task.durationMinutes(), start, finish,
                    task.lateStart(), task.lateFinish(), floatMinutes, floatMinutes == 0L,
                    task.deadlineOverrun()));
            if (assignee != null && task.durationMinutes() > 0L) {
                cursorByAssignee.put(assignee, finish);
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

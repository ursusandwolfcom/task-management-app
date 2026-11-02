package com.example.taskmanagement.planning.core;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Earliest/latest schedule with unlimited parallel resources. Missing estimates are zero-length
 * milestones. Arithmetic uses working-minute offsets from the project origin, then maps to Instants.
 * Task/project due dates flag overrun without changing the critical path.
 */
public final class CriticalPathCalculator {

    private final PriorityTopologicalSorter sorter;

    public CriticalPathCalculator(PriorityTopologicalSorter sorter) {
        this.sorter = Objects.requireNonNull(sorter, "sorter");
    }

    public ProjectSchedule schedule(GraphSnapshot graph, WorkingCalendar calendar, Instant projectOrigin) {
        return schedule(graph, calendar, projectOrigin, null);
    }

    public ProjectSchedule schedule(GraphSnapshot graph, WorkingCalendar calendar, Instant projectOrigin,
            LocalDate projectDeadline) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(calendar, "calendar");
        Objects.requireNonNull(projectOrigin, "projectOrigin");
        var origin = calendar.normalizeStart(projectOrigin);
        if (graph.nodes().isEmpty()) {
            return new ProjectSchedule(origin, origin, 0L, false, List.of(), List.of(), List.of());
        }
        var order = sorter.executionOrder(graph);
        var duration = new HashMap<Long, Long>();
        var earlyStart = new HashMap<Long, Long>();
        var earlyFinish = new HashMap<Long, Long>();
        long projectDuration = 0L;
        for (var node : order) {
            long minutes = node.estimatedMinutes() == null ? 0L : node.estimatedMinutes();
            duration.put(node.id(), minutes);
            long start = 0L;
            for (long predecessor : graph.predecessors(node.id())) {
                start = Math.max(start, earlyFinish.get(predecessor));
            }
            long finish = start + minutes;
            earlyStart.put(node.id(), start);
            earlyFinish.put(node.id(), finish);
            projectDuration = Math.max(projectDuration, finish);
        }
        var lateFinish = new HashMap<Long, Long>();
        var lateStart = new HashMap<Long, Long>();
        for (int index = order.size() - 1; index >= 0; index--) {
            var node = order.get(index);
            long finish = projectDuration;
            for (long successor : graph.successors(node.id())) {
                finish = Math.min(finish, lateStart.get(successor));
            }
            long minutes = duration.get(node.id());
            lateFinish.put(node.id(), finish);
            lateStart.put(node.id(), finish - minutes);
        }
        var tasks = new ArrayList<TaskSchedule>(order.size());
        var criticalIds = new ArrayList<Long>();
        for (var node : order) {
            long floatMinutes = lateStart.get(node.id()) - earlyStart.get(node.id());
            boolean critical = floatMinutes == 0L;
            Instant finishInstant = calendar.addWorkingMinutes(origin, earlyFinish.get(node.id()));
            boolean overrun = isOverrun(node.dueDate(), finishInstant, calendar);
            tasks.add(new TaskSchedule(
                    node.id(),
                    duration.get(node.id()),
                    calendar.addWorkingMinutes(origin, earlyStart.get(node.id())),
                    finishInstant,
                    calendar.addWorkingMinutes(origin, lateStart.get(node.id())),
                    calendar.addWorkingMinutes(origin, lateFinish.get(node.id())),
                    floatMinutes,
                    critical,
                    overrun));
            if (critical) {
                criticalIds.add(node.id());
            }
        }
        criticalIds.sort(Comparator.naturalOrder());
        var criticalEdges = graph.edges().stream()
                .filter(edge -> earlyFinish.get(edge.predecessorId()).equals(earlyStart.get(edge.successorId())))
                .filter(edge -> lateStart.get(edge.predecessorId()) - earlyStart.get(edge.predecessorId()) == 0L)
                .filter(edge -> lateStart.get(edge.successorId()) - earlyStart.get(edge.successorId()) == 0L)
                .sorted(Comparator.comparingLong(DependencyEdge::predecessorId)
                        .thenComparingLong(DependencyEdge::successorId))
                .toList();
        Instant projectFinish = calendar.addWorkingMinutes(origin, projectDuration);
        boolean projectOverrun = isOverrun(projectDeadline, projectFinish, calendar);
        return new ProjectSchedule(origin, projectFinish, projectDuration, projectOverrun, tasks, criticalIds,
                criticalEdges);
    }

    /** Due date is calendar day inclusive; finish after that local day end is overrun. */
    static boolean isOverrun(LocalDate deadline, Instant finish, WorkingCalendar calendar) {
        if (deadline == null) {
            return false;
        }
        var finishDay = finish.atZone(calendar.zone()).toLocalDate();
        return finishDay.isAfter(deadline);
    }
}

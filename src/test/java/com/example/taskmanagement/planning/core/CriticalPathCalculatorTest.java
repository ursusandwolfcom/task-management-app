package com.example.taskmanagement.planning.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CriticalPathCalculatorTest {

    private final CriticalPathCalculator calculator = new CriticalPathCalculator(new PriorityTopologicalSorter());
    private final WorkingCalendar calendar = new WorkingCalendar(ZoneId.of("UTC"));

    @Test
    void emptyGraphHasZeroDuration() {
        var origin = calendar.projectOrigin(LocalDate.of(2026, 11, 2));
        var schedule = calculator.schedule(GraphSnapshot.of(List.of(), List.of()), calendar, origin);
        assertEquals(0L, schedule.durationMinutes());
        assertEquals(origin, schedule.projectStart());
        assertEquals(origin, schedule.projectFinish());
        assertFalse(schedule.projectDeadlineOverrun());
        assertTrue(schedule.tasks().isEmpty());
    }

    @Test
    void chainIsFullyCriticalAndParallelBranchHasFloat() {
        var a = new TaskNode(1L, 10, null, 480L);
        var b = new TaskNode(2L, 10, null, 480L);
        var c = new TaskNode(3L, 10, null, 240L);
        var graph = GraphSnapshot.of(List.of(a, b, c), List.of(
                new DependencyEdge(1L, 2L),
                new DependencyEdge(1L, 3L)));
        var origin = calendar.projectOrigin(LocalDate.of(2026, 11, 2));
        var schedule = calculator.schedule(graph, calendar, origin);
        assertEquals(960L, schedule.durationMinutes());
        assertEquals(Set.of(1L, 2L), Set.copyOf(schedule.criticalTaskIds()));
        var byId = schedule.tasks().stream().collect(java.util.stream.Collectors.toMap(TaskSchedule::taskId, t -> t));
        assertTrue(byId.get(1L).critical());
        assertTrue(byId.get(2L).critical());
        assertFalse(byId.get(3L).critical());
        assertEquals(240L, byId.get(3L).floatMinutes());
        assertEquals(List.of(new DependencyEdge(1L, 2L)), schedule.criticalEdges());
    }

    @Test
    void disconnectedTasksRunInParallelFromOrigin() {
        var a = new TaskNode(1L, 10, null, 120L);
        var b = new TaskNode(2L, 20, null, 480L);
        var graph = GraphSnapshot.of(List.of(a, b), List.of());
        var origin = calendar.projectOrigin(LocalDate.of(2026, 11, 2));
        var schedule = calculator.schedule(graph, calendar, origin);
        assertEquals(480L, schedule.durationMinutes());
        assertEquals(List.of(2L), schedule.criticalTaskIds());
        assertEquals(origin, schedule.tasks().get(0).earlyStart());
        assertEquals(origin, schedule.tasks().get(1).earlyStart());
    }

    @Test
    void deadlineOverrunDoesNotChangeCriticalPath() {
        long day = WorkingCalendar.MINUTES_PER_DAY;
        var a = new TaskNode(1L, 10, LocalDate.of(2026, 11, 2), day);
        var b = new TaskNode(2L, 10, LocalDate.of(2026, 11, 2), day);
        var graph = GraphSnapshot.of(List.of(a, b), List.of(new DependencyEdge(1L, 2L)));
        var origin = calendar.projectOrigin(LocalDate.of(2026, 11, 2));
        var schedule = calculator.schedule(graph, calendar, origin, LocalDate.of(2026, 11, 2));
        assertEquals(List.of(1L, 2L), schedule.criticalTaskIds());
        assertTrue(schedule.projectDeadlineOverrun());
        var byId = schedule.tasks().stream().collect(java.util.stream.Collectors.toMap(TaskSchedule::taskId, t -> t));
        assertFalse(byId.get(1L).deadlineOverrun());
        assertTrue(byId.get(2L).deadlineOverrun());
        assertTrue(byId.get(2L).critical());
    }
}

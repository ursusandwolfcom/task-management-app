package com.example.taskmanagement.planning.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceLevelerTest {
    private final WorkingCalendar calendar = new WorkingCalendar(ZoneOffset.UTC);

    @Test
    void serializesOverlappingAssigneeTasks() {
        Instant nine = Instant.parse("2026-11-02T09:00:00Z");
        Instant twelve = Instant.parse("2026-11-02T12:00:00Z");
        Instant eleven = Instant.parse("2026-11-02T11:00:00Z");
        Instant fifteen = Instant.parse("2026-11-02T15:00:00Z");
        var a = new TaskSchedule(1L, 180L, nine, twelve, nine, twelve, 0L, true, false);
        var b = new TaskSchedule(2L, 180L, eleven, fifteen, eleven, fifteen, 0L, true, false);
        var leveled = ResourceLeveler.level(List.of(a, b), Map.of(1L, 10L, 2L, 10L), calendar);
        assertThat(leveled.get(0).earlyStart()).isEqualTo(nine);
        assertThat(leveled.get(0).earlyFinish()).isEqualTo(twelve);
        assertThat(leveled.get(1).earlyStart()).isEqualTo(twelve);
        assertThat(leveled.get(1).earlyFinish()).isEqualTo(calendar.addWorkingMinutes(twelve, 180L));
        assertThat(leveled.get(1).earlyStart().isBefore(leveled.get(0).earlyFinish())).isFalse();
    }

    @Test
    void ignoresTasksWithoutAssignee() {
        Instant nine = Instant.parse("2026-11-02T09:00:00Z");
        Instant twelve = Instant.parse("2026-11-02T12:00:00Z");
        Instant eleven = Instant.parse("2026-11-02T11:00:00Z");
        Instant fifteen = Instant.parse("2026-11-02T15:00:00Z");
        var a = new TaskSchedule(1L, 180L, nine, twelve, nine, twelve, 0L, true, false);
        var b = new TaskSchedule(2L, 180L, eleven, fifteen, eleven, fifteen, 0L, true, false);
        var leveled = ResourceLeveler.level(List.of(a, b), Map.of(1L, 10L), calendar);
        assertThat(leveled.get(1).earlyStart()).isEqualTo(eleven);
    }

    @Test
    void appliesAssigneeDailyCapacityWhenLeveling() {
        Instant nine = Instant.parse("2026-11-02T09:00:00Z");
        Instant twelve = Instant.parse("2026-11-02T12:00:00Z");
        var capped = calendar.withDailyCapacityMinutes(210);
        Instant firstFinish = capped.addWorkingMinutes(nine, 210L);
        assertThat(firstFinish).isEqualTo(Instant.parse("2026-11-02T12:30:00Z"));
        var a = new TaskSchedule(1L, 210L, nine, twelve, nine, twelve, 0L, true, false);
        var b = new TaskSchedule(2L, 60L, nine, twelve, nine, twelve, 0L, true, false);
        var leveled = ResourceLeveler.level(List.of(a, b), Map.of(1L, 10L, 2L, 10L), calendar, Map.of(10L, 210));
        assertThat(leveled.get(0).taskId()).isEqualTo(1L);
        assertThat(leveled.get(0).earlyFinish()).isEqualTo(firstFinish);
        // Capacity exhausted at firstFinish → second task starts next working morning.
        Instant secondStart = capped.normalizeStart(firstFinish);
        assertThat(secondStart).isEqualTo(Instant.parse("2026-11-03T09:00:00Z"));
        assertThat(leveled.get(1).earlyStart()).isEqualTo(secondStart);
        assertThat(leveled.get(1).earlyFinish()).isEqualTo(capped.addWorkingMinutes(secondStart, 60L));
    }

    @Test
    void appliesAssigneeBreakOverrideWhenLeveling() {
        Instant nine = Instant.parse("2026-11-02T09:00:00Z");
        Instant twelve = Instant.parse("2026-11-02T12:00:00Z");
        var shortBreaks = List.of(new BreakWindow(java.time.LocalTime.of(12, 0), java.time.LocalTime.of(12, 30)));
        var assigneeCalendar = new WorkingCalendar(calendar.zone(), calendar.holidays(), shortBreaks);
        var a = new TaskSchedule(1L, 180L, nine, twelve, nine, twelve, 0L, true, false);
        var b = new TaskSchedule(2L, 180L, nine, twelve, nine, twelve, 0L, true, false);
        var leveled = ResourceLeveler.level(List.of(a, b), Map.of(1L, 10L, 2L, 10L), calendar, Map.of(),
                Map.of(10L, shortBreaks));
        Instant firstFinish = assigneeCalendar.addWorkingMinutes(nine, 180L);
        assertThat(leveled.get(0).earlyFinish()).isEqualTo(firstFinish);
        Instant secondStart = assigneeCalendar.normalizeStart(firstFinish);
        assertThat(secondStart).isEqualTo(Instant.parse("2026-11-02T12:30:00Z"));
        assertThat(leveled.get(1).earlyStart()).isEqualTo(secondStart);
        assertThat(leveled.get(1).earlyFinish())
                .isEqualTo(assigneeCalendar.addWorkingMinutes(secondStart, 180L));
    }

    @Test
    void propagatesCapacityDelaysThroughForksAndUnassignedTasks() {
        var graph = GraphSnapshot.of(List.of(
                new TaskNode(1L, 0, null, 120L), new TaskNode(2L, 0, null, 180L),
                new TaskNode(3L, 0, null, 60L), new TaskNode(4L, 0, null, null),
                new TaskNode(5L, 0, null, 60L)),
                List.of(new DependencyEdge(2L, 3L), new DependencyEdge(2L, 4L), new DependencyEdge(4L, 5L)));
        var original = new CriticalPathCalculator(new PriorityTopologicalSorter()).schedule(graph, calendar,
                Instant.parse("2026-11-02T09:00:00Z")).tasks();
        var breaks = List.of(new BreakWindow(java.time.LocalTime.NOON, java.time.LocalTime.of(12, 30)));
        var leveled = ResourceLeveler.level(original, Map.of(1L, 10L, 2L, 10L, 3L, 20L), calendar,
                Map.of(10L, 210), Map.of(10L, breaks), List.copyOf(graph.edges()));
        var byId = leveled.stream().collect(java.util.stream.Collectors.toMap(
                TaskSchedule::taskId, java.util.function.Function.identity()));
        assertThat(byId.get(2L).earlyFinish()).isEqualTo(Instant.parse("2026-11-03T10:30:00Z"));
        for (var edge : graph.edges()) {
            assertThat(byId.get(edge.successorId()).earlyStart())
                    .isAfterOrEqualTo(byId.get(edge.predecessorId()).earlyFinish());
        }
        assertThat(byId.get(4L).earlyStart()).isEqualTo(byId.get(4L).earlyFinish());
    }

    @Test
    void ordersZeroDurationDependencyChainsBeforeLowerIdSuccessorsDeterministically() {
        var graph = GraphSnapshot.of(List.of(
                new TaskNode(1L, 0, null, 420L), new TaskNode(2L, 0, null, null),
                new TaskNode(3L, 0, null, null), new TaskNode(4L, 0, null, 420L)),
                List.of(new DependencyEdge(4L, 3L), new DependencyEdge(3L, 2L)));
        var original = new CriticalPathCalculator(new PriorityTopologicalSorter()).schedule(graph, calendar,
                Instant.parse("2026-11-02T09:00:00Z")).tasks();
        Map<Long, TaskSchedule> expected = null;
        for (var input : List.of(original, original.reversed())) {
            var leveled = ResourceLeveler.level(input, Map.of(1L, 10L, 4L, 10L), calendar, Map.of(),
                    Map.of(), List.copyOf(graph.edges()));
            var byId = leveled.stream().collect(java.util.stream.Collectors.toMap(
                    TaskSchedule::taskId, java.util.function.Function.identity()));
            assertThat(byId.get(4L).earlyFinish()).isEqualTo(Instant.parse("2026-11-03T17:00:00Z"));
            assertThat(byId.get(3L).earlyStart()).isEqualTo(byId.get(4L).earlyFinish());
            assertThat(byId.get(2L).earlyStart()).isEqualTo(byId.get(3L).earlyFinish());
            if (expected != null) {
                assertThat(byId).isEqualTo(expected);
            }
            expected = byId;
        }
    }

    @Test
    void rejectsCyclicLevelingInputRatherThanReturningPartialSchedule() {
        Instant nine = Instant.parse("2026-11-02T09:00:00Z");
        var task = new TaskSchedule(1L, 0L, nine, nine, nine, nine, 0L, true, false);
        var other = new TaskSchedule(2L, 0L, nine, nine, nine, nine, 0L, true, false);
        assertThatThrownBy(() -> ResourceLeveler.level(List.of(task, other),
                Map.of(), calendar, Map.of(), Map.of(),
                List.of(new DependencyEdge(1L, 2L), new DependencyEdge(2L, 1L))))
                .isInstanceOf(GraphValidationException.class)
                .extracting("reason").isEqualTo(GraphValidationException.Reason.CYCLIC_DEPENDENCY);
    }

    @Test
    void normalizesFirstTaskStartAgainstAssigneeCalendar() {
        Instant noon = Instant.parse("2026-11-02T12:00:00Z");
        Instant fourteen = Instant.parse("2026-11-02T14:00:00Z");
        var task = new TaskSchedule(1L, 60L, noon, fourteen, noon, fourteen, 0L, true, false);
        var breaks = List.of(new BreakWindow(java.time.LocalTime.NOON, java.time.LocalTime.of(13, 0)));
        var leveled = ResourceLeveler.level(List.of(task), Map.of(1L, 10L), calendar, Map.of(),
                Map.of(10L, breaks));
        assertThat(leveled.getFirst().earlyStart()).isEqualTo(Instant.parse("2026-11-02T13:00:00Z"));
        assertThat(leveled.getFirst().earlyFinish()).isEqualTo(fourteen);
    }

    @Test
    void normalizesFirstTaskStartAfterDailyCapacityIsExhausted() {
        Instant fourteen = Instant.parse("2026-11-02T14:00:00Z");
        Instant fifteen = Instant.parse("2026-11-02T15:00:00Z");
        var task = new TaskSchedule(1L, 60L, fourteen, fifteen, fourteen, fifteen, 0L, true, false);
        var leveled = ResourceLeveler.level(List.of(task), Map.of(1L, 10L), calendar, Map.of(10L, 210));
        assertThat(leveled.getFirst().earlyStart()).isEqualTo(Instant.parse("2026-11-03T09:00:00Z"));
        assertThat(leveled.getFirst().earlyFinish()).isEqualTo(Instant.parse("2026-11-03T10:00:00Z"));
    }
}

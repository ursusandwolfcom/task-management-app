package com.example.taskmanagement.planning.core;

import static org.assertj.core.api.Assertions.assertThat;

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
}

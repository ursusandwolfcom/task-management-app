package com.example.taskmanagement.task.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.planning.core.TaskSchedule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceContentionTest {
    @Test
    void overlappingSameAssigneeIsContended() {
        var a = schedule(1L, "2026-11-02T09:00:00Z", "2026-11-02T12:00:00Z");
        var b = schedule(2L, "2026-11-02T11:00:00Z", "2026-11-02T15:00:00Z");
        var c = schedule(3L, "2026-11-02T09:00:00Z", "2026-11-02T10:00:00Z");
        assertThat(ResourceContention.contendedTaskIds(List.of(a, b, c), Map.of(1L, 10L, 2L, 10L, 3L, 11L)))
                .isEqualTo(Set.of(1L, 2L));
    }

    @Test
    void touchingExclusiveBoundsDoNotOverlap() {
        var a = schedule(1L, "2026-11-02T09:00:00Z", "2026-11-02T12:00:00Z");
        var b = schedule(2L, "2026-11-02T12:00:00Z", "2026-11-02T15:00:00Z");
        assertThat(ResourceContention.contendedTaskIds(List.of(a, b), Map.of(1L, 10L, 2L, 10L))).isEmpty();
    }

    private static TaskSchedule schedule(long id, String start, String finish) {
        Instant earlyStart = Instant.parse(start);
        Instant earlyFinish = Instant.parse(finish);
        return new TaskSchedule(id, 60L, earlyStart, earlyFinish, earlyStart, earlyFinish, 0L, false, false);
    }
}

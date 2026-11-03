package com.example.taskmanagement.planning.core;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

/** Half-open working-day break {@code [start, end)} inside Mon–Fri 09:00–17:00. */
public record BreakWindow(LocalTime start, LocalTime end) {

    public BreakWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Break end must be after start");
        }
        if (start.isBefore(WorkingCalendar.DAY_START) || end.isAfter(WorkingCalendar.DAY_END)) {
            throw new IllegalArgumentException("Break must lie within the working day");
        }
    }

    public long minutes() {
        return Duration.between(start, end).toMinutes();
    }
}

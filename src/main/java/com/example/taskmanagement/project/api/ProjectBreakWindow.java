package com.example.taskmanagement.project.api;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Half-open project break {@code [start, end)} inside Mon–Fri 09:00–17:00. */
public record ProjectBreakWindow(LocalTime start, LocalTime end) {

    public static final LocalTime DAY_START = LocalTime.of(9, 0);
    public static final LocalTime DAY_END = LocalTime.of(17, 0);
    public static final ProjectBreakWindow DEFAULT_LUNCH =
            new ProjectBreakWindow(LocalTime.of(13, 0), LocalTime.of(14, 0));
    public static final List<ProjectBreakWindow> DEFAULT_BREAKS = List.of(DEFAULT_LUNCH);

    public ProjectBreakWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Break end must be after start");
        }
        if (start.isBefore(DAY_START) || end.isAfter(DAY_END)) {
            throw new IllegalArgumentException("Break must lie within the working day");
        }
    }

    public static List<ProjectBreakWindow> normalize(List<ProjectBreakWindow> input) {
        Objects.requireNonNull(input, "breaks");
        var sorted = input.stream().sorted(Comparator.comparing(ProjectBreakWindow::start)).toList();
        List<ProjectBreakWindow> normalized = new ArrayList<>(sorted.size());
        LocalTime previousEnd = null;
        for (ProjectBreakWindow window : sorted) {
            if (previousEnd != null && window.start().isBefore(previousEnd)) {
                throw new IllegalArgumentException("Break windows must not overlap");
            }
            normalized.add(window);
            previousEnd = window.end();
        }
        return List.copyOf(normalized);
    }
}

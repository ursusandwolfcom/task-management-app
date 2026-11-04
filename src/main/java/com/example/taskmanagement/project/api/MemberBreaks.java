package com.example.taskmanagement.project.api;

import jakarta.validation.constraints.Positive;
import java.util.List;

/** Stored per-member break overrides; missing members use the project break windows. */
public record MemberBreaks(@Positive long userId, List<ProjectBreakWindow> windows) {
    public MemberBreaks {
        windows = windows == null ? List.of() : List.copyOf(windows);
    }
}

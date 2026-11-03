package com.example.taskmanagement.project.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface ProjectOperations {
    record ProjectPage(List<ProjectView> content, int page, int size, long totalElements, int totalPages) {
        public ProjectPage {
            content = List.copyOf(content);
        }
    }

    record MemberView(long userId, boolean owner) {
    }

    record MemberPage(List<MemberView> content, int page, int size, long totalElements, int totalPages) {
        public MemberPage {
            content = List.copyOf(content);
        }
    }

    ProjectView create(long actorId, @NotNull @Valid ProjectData data);

    ProjectView read(long actorId, long projectId);

    ProjectPage list(long actorId, int page, int size);

    ProjectView replace(long actorId, long projectId, @NotNull @Valid ProjectData data,
            @PositiveOrZero long version);

    ProjectView patch(long actorId, long projectId, @NotNull ProjectPatch patch, @PositiveOrZero long version);

    MemberPage members(long actorId, long projectId, int page, int size);

    ProjectView addMember(long actorId, long projectId, long userId, @PositiveOrZero long version);

    ProjectView removeMember(long actorId, long projectId, long userId, @PositiveOrZero long version);

    ProjectView transferOwner(long actorId, long projectId, long userId, @PositiveOrZero long version);

    /** Deletes a project without tasks; owned collaboration metadata is removed with its parent. */
    void deleteEmpty(long actorId, long projectId, @PositiveOrZero long version);

    Set<LocalDate> holidays(long actorId, long projectId);

    /** Replaces the full holiday set and bumps calendarRevision; requires current project version. */
    ProjectView replaceHolidays(long actorId, long projectId, Set<LocalDate> holidays, @PositiveOrZero long version);

    /** Effective break windows (default lunch when none stored). */
    List<ProjectBreakWindow> breaks(long actorId, long projectId);

    /**
     * Replaces stored break windows and bumps calendarRevision; empty list restores the default lunch.
     * Requires current project version.
     */
    ProjectView replaceBreaks(long actorId, long projectId, List<ProjectBreakWindow> breaks,
            @PositiveOrZero long version);

    /** Stored member capacities only (not expanded with defaults). */
    List<MemberCapacity> capacities(long actorId, long projectId);

    /**
     * Replaces stored member daily capacities and bumps calendarRevision.
     * Users must be current project members; empty list clears custom capacities.
     */
    ProjectView replaceCapacities(long actorId, long projectId, List<MemberCapacity> capacities,
            @PositiveOrZero long version);
}

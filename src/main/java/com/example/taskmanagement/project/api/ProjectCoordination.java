package com.example.taskmanagement.project.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Transactional contract for future task/scheduling writers; calls require an existing transaction. */
public interface ProjectCoordination {
    enum Permission {
        READ, WRITE_TASKS, MANAGE_PROJECT
    }

    ProjectView lockAndAuthorize(long actorId, long projectId, @NotNull Permission permission);

    ProjectView markInputChanged(long actorId, long projectId);

    boolean isMember(long projectId, long userId);

    /** Holiday dates for a project already locked in the current transaction. */
    Set<LocalDate> holidays(long projectId);

    /**
     * Effective break windows for a project already locked in the current transaction.
     * Empty storage means the default lunch break.
     */
    List<ProjectBreakWindow> breaks(long projectId);

    /** Stored per-member daily capacity minutes; missing members use the project calendar day. */
    Map<Long, Integer> capacities(long projectId);
}

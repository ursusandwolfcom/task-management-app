package com.example.taskmanagement.task.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Internal background API; no HTTP endpoint or bypass for user requests. */
public interface TaskDeadlineOperations {
    record Candidate(long taskId, long projectId, String taskName, LocalDate dueDate,
            Long assigneeId, long ownerId, String timezone, boolean eligible) {
    }

    List<Long> scanAfter(long taskId, int limit);

    /** Requires a transaction; re-reads task metadata under the project mutation lock. */
    Optional<Candidate> lockCandidate(long taskId);

    boolean mayReceive(long taskId, long projectId, LocalDate dueDate, long recipientId);
}

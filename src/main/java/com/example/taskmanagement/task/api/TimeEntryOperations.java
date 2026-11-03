package com.example.taskmanagement.task.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public interface TimeEntryOperations {
    record TimeEntryPage(
            List<TimeEntryView> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            long totalMinutes) {
        public TimeEntryPage {
            content = List.copyOf(content);
        }
    }

    TimeEntryView create(long actorId, @NotNull @Valid TimeEntryData data);

    TimeEntryPage list(long actorId, long taskId, int page, int size);

    void delete(long actorId, long entryId);
}

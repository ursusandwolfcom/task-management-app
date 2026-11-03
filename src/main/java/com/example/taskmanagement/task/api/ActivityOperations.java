package com.example.taskmanagement.task.api;

import java.util.List;

public interface ActivityOperations {
    record ActivityPage(List<ActivityView> content, int page, int size, long totalElements, int totalPages) {
        public ActivityPage {
            content = List.copyOf(content);
        }
    }

    void record(long actorId, long projectId, long taskId, String actionType, String summary);

    ActivityPage list(long actorId, long taskId, int page, int size);
}

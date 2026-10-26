package com.example.taskmanagement.collaboration.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public interface CommentOperations {
    record CommentPage(List<CommentView> content, int page, int size, long totalElements, int totalPages) {
        public CommentPage {
            content = List.copyOf(content);
        }
    }

    CommentView create(long actorId, @NotNull @Valid CommentData data);

    CommentPage list(long actorId, long taskId, int page, int size);

    void delete(long actorId, long commentId);
}

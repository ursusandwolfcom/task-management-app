package com.example.taskmanagement.collaboration.api;

import java.time.Instant;

public record CommentView(long id, long projectId, long taskId, long authorId, String text, Instant createdAt) {
}

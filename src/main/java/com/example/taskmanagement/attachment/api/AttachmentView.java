package com.example.taskmanagement.attachment.api;

import java.time.Instant;

public record AttachmentView(long id, long projectId, long taskId, long uploaderId, String fileName,
        String contentType, long sizeBytes, Instant createdAt) {
}

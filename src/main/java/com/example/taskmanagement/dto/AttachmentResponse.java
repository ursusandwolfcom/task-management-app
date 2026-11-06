package com.example.taskmanagement.dto;

import java.time.LocalDateTime;

public record AttachmentResponse(
        Long id, Long taskId, String dropboxFileId, String filename, LocalDateTime uploadDate) {
}

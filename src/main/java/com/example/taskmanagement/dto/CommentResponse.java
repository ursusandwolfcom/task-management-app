package com.example.taskmanagement.dto;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id, Long taskId, Long userId, String text, LocalDateTime timestamp) {
}

package com.example.taskmanagement.collaboration.api;

import com.example.taskmanagement.shared.validation.NoNul;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Plain text only; author and UTC timestamp are server-owned facts. */
public record CommentData(@Positive long taskId,
        @NotBlank @Size(max = 10000) @NoNul String text) {
    public CommentData {
        text = text == null ? null : text.strip();
    }
}

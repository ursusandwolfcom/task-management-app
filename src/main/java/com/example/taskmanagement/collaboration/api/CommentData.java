package com.example.taskmanagement.collaboration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Plain text only; author and UTC timestamp are server-owned facts. */
public record CommentData(@Positive long taskId,
        @NotBlank @Size(max = 10000) @Pattern(regexp = "[^\\x00]*") String text) {
    public CommentData {
        text = text == null ? null : text.strip();
    }
}

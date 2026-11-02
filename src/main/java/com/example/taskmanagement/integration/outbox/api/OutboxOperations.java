package com.example.taskmanagement.integration.outbox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Transactional outbox for durable side effects; consumers live in other modules. */
public interface OutboxOperations {
    void append(@NotBlank @Size(max = 100) String eventType, @NotBlank @Size(max = 4000) String payload);
}

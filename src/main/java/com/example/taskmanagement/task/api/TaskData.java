package com.example.taskmanagement.task.api;

import com.example.taskmanagement.shared.validation.NoNul;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

public record TaskData(@NotBlank @Size(max = 200) @NoNul String name,
        @Size(max = 10000) @NoNul String description, @NotNull TaskPriority priority,
        @Schema(description = "Optional whole minutes, 15 through 525600 (365 physical days)")
        @Min(15) @Max(525600) Integer estimatedMinutes,
        @Schema(description = "Optional PostgreSQL DATE: -4713-11-24 through +5874897-12-31")
        LocalDate dueDate, @Positive Long assigneeId) {
    public TaskData {
        name = name == null ? null : name.strip();
        description = description == null ? "" : description.strip();
    }

    public void validateDate() {
        if (dueDate != null && (dueDate.isBefore(LocalDate.of(-4713, 11, 24))
                || dueDate.isAfter(LocalDate.of(5874897, 12, 31)))) {
            throw new TaskException(TaskException.Reason.VALIDATION_ERROR);
        }
    }
}

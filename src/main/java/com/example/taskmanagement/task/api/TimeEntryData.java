package com.example.taskmanagement.task.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TimeEntryData(
        @Positive long taskId,
        @NotNull LocalDate workDate,
        @Min(1) @Max(1440) int minutes,
        @Size(max = 500) String note) {
}

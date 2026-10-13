package com.example.taskmanagement.project.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.ZoneId;

public record ProjectData(@NotBlank @Size(max = 200) String name,
        @Size(max = 10000) String description, @NotNull LocalDate startDate,
        @NotNull LocalDate endDate, @NotBlank @Size(max = 64) String timezone) {
    public ProjectData {
        name = name == null ? null : name.strip();
        description = description == null ? "" : description.strip();
        timezone = timezone == null ? "UTC" : timezone.strip();
    }

    public void validateDatesAndTimezone() {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
                || !ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new ProjectException(ProjectException.Reason.VALIDATION_ERROR);
        }
    }
}

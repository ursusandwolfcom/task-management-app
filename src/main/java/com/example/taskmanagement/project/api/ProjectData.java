package com.example.taskmanagement.project.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.ZoneId;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectData(@NotBlank @Size(max = 200) String name,
        @Size(max = 10000) String description,
        @Schema(description = "PostgreSQL DATE range: -4713-11-24 through +5874897-12-31 (ISO years)")
        @NotNull LocalDate startDate,
        @Schema(description = "Same PostgreSQL DATE range as startDate; must be on or after startDate")
        @NotNull LocalDate endDate, @NotBlank @Size(max = 64) String timezone) {
    // PostgreSQL 17 timestamp.h: Julian day zero through DATE_END_JULIAN - 1.
    private static final LocalDate MIN_DATE = LocalDate.of(-4713, 11, 24);
    private static final LocalDate MAX_DATE = LocalDate.of(5874897, 12, 31);
    public ProjectData {
        name = name == null ? null : name.strip();
        description = description == null ? "" : description.strip();
        timezone = timezone == null ? "UTC" : timezone.strip();
    }

    public void validateDatesAndTimezone() {
        if (!isStoredDate(startDate) || !isStoredDate(endDate) || endDate.isBefore(startDate)
                || !ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new ProjectException(ProjectException.Reason.VALIDATION_ERROR);
        }
    }

    private static boolean isStoredDate(LocalDate date) {
        return date != null && !date.isBefore(MIN_DATE) && !date.isAfter(MAX_DATE);
    }
}

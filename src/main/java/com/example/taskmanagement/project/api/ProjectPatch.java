package com.example.taskmanagement.project.api;

import java.time.LocalDate;

/** Null fields retain current metadata; access and version are checked under the project lock. */
public record ProjectPatch(String name, String description, LocalDate startDate, LocalDate endDate, String timezone) {
    public ProjectData applyTo(ProjectView current) {
        return new ProjectData(name == null ? current.name() : name,
                description == null ? current.description() : description,
                startDate == null ? current.startDate() : startDate,
                endDate == null ? current.endDate() : endDate,
                timezone == null ? current.timezone() : timezone);
    }
}

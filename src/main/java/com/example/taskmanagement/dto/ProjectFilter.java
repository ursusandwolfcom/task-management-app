package com.example.taskmanagement.dto;

import com.example.taskmanagement.model.ProjectStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record ProjectFilter(
        @Size(max = 200) String name, ProjectStatus status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endTo) {
}

package com.example.taskmanagement.dto;

import com.example.taskmanagement.model.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

public record ProjectRequest(
        @NotBlank @Size(max = 200) String name, @Size(max = 4000) String description,
        LocalDate startDate, LocalDate endDate, @NotNull ProjectStatus status,
        @NotNull @Size(max = 100) Set<@NotNull Long> memberIds) {
}

package com.example.taskmanagement.dto;

import com.example.taskmanagement.model.Role;
import jakarta.validation.constraints.NotNull;

public record RoleRequest(
        @NotNull Role role) {
}

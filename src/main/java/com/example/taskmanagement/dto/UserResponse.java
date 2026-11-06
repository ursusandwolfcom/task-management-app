package com.example.taskmanagement.dto;

import com.example.taskmanagement.model.Role;

public record UserResponse(
        Long id, String username, String email, String firstName, String lastName, Role role) {
}

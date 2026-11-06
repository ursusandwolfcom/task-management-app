package com.example.taskmanagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfilePatch(
        @Email @Pattern(regexp = ".*\\S.*") @Size(max = 254) String email,
        @Pattern(regexp = ".*\\S.*") @Size(max = 100) String firstName,
        @Pattern(regexp = ".*\\S.*") @Size(max = 100) String lastName) {
}

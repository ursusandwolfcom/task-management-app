package com.example.taskmanagement.dto;

import com.example.taskmanagement.security.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 254) String identifier,
        @NotBlank @Size(max = PasswordPolicy.MAX_BYTES) String password) {
    @Override
    public String toString() {
        return "LoginRequest[credentials redacted]";
    }
}

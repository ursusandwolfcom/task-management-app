package com.example.taskmanagement.dto;

import com.example.taskmanagement.security.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,64}") String username,
        @NotBlank @Size(min = PasswordPolicy.MIN_CHARACTERS, max = PasswordPolicy.MAX_BYTES) String password,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName) {
    @Override
    public String toString() {
        return "RegisterRequest[credentials redacted]";
    }
}

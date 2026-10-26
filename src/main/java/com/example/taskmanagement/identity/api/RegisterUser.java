package com.example.taskmanagement.identity.api;

import com.example.taskmanagement.shared.validation.NoNul;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RegisterUser(
        @NotBlank @Pattern(regexp = "[a-z0-9_.-]{3,50}") String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 100) @NoNul String firstName,
        @NotBlank @Size(max = 100) @NoNul String lastName) {
    public RegisterUser {
        username = normalize(username);
        email = normalize(email);
        firstName = trim(firstName);
        lastName = trim(lastName);
    }

    public static String normalize(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    public static String trim(String value) {
        return value == null ? null : value.strip();
    }

    @Override
    public String toString() {
        return "RegisterUser[credentials redacted]";
    }
}

package com.example.taskmanagement.identity.api;

import com.example.taskmanagement.shared.validation.NoNul;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProfileChange(
        @NotBlank @Pattern(regexp = "[a-z0-9_.-]{3,50}") String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) @NoNul String firstName,
        @NotBlank @Size(max = 100) @NoNul String lastName,
        @NotNull @PositiveOrZero Long version) {
    public ProfileChange {
        username = RegisterUser.normalize(username);
        email = RegisterUser.normalize(email);
        firstName = RegisterUser.trim(firstName);
        lastName = RegisterUser.trim(lastName);
    }
}

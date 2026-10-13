package com.example.taskmanagement.identity.api;

/** Public profile deliberately contains no credentials or persistence entity. */
public record UserView(long id, String username, String email, String firstName, String lastName,
        UserRole role, long version) {
}

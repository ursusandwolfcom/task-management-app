package com.example.taskmanagement.identity.api;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Credentials and sessions stay inside identity; security consumes only this API. */
public interface IdentityOperations {
    record SessionGrant(UserView user, UUID sessionId, long credentialVersion,
            String refreshToken, Instant refreshExpiresAt) {
        @Override
        public String toString() {
            return "SessionGrant[credentials redacted]";
        }
    }

    UserView register(@NotNull @Valid RegisterUser command);

    UserView bootstrapAdmin(@NotNull @Valid RegisterUser command);

    SessionGrant login(@NotBlank @Size(max = 254) String identifier, @NotBlank @Size(max = 128) String password);

    SessionGrant refresh(@NotBlank @Size(min = 43, max = 43) String token);

    UserView actor(long userId, UUID sessionId, long credentialVersion);

    UserView profile(long userId);

    /** Reads the current database role even if a caller has already loaded the user in its transaction. */
    UserRole currentRole(long userId);

    UserView updateProfile(long userId, @NotNull @Valid ProfileChange command);

    UserView changeRole(long actorId, long userId, @NotNull UserRole role, @PositiveOrZero long version);

    void changePassword(long userId, @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword);

    void logout(long userId, UUID sessionId);
}

package com.example.taskmanagement.identity.web;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.ProfileChange;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.identity.api.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/users", "/users"})
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    public record ProfilePatch(String username, String email, String firstName, String lastName,
            @NotNull @PositiveOrZero Long version) {
    }

    public record RoleChange(@NotNull UserRole role, @NotNull @PositiveOrZero Long version) {
    }

    public record PasswordChange(@NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
        @Override
        public String toString() {
            return "PasswordChange[credentials redacted]";
        }
    }

    private final IdentityOperations identity;

    public UserController(IdentityOperations identity) {
        this.identity = identity;
    }

    @GetMapping("/me")
    @Operation(summary = "Read the authenticated user's profile")
    public ResponseEntity<UserView> me(@AuthenticationPrincipal Jwt jwt) {
        return profile(identity.profile(userId(jwt)));
    }

    @PutMapping("/me")
    @Operation(summary = "Replace own profile with an expected version")
    public ResponseEntity<UserView> update(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileChange command) {
        return profile(identity.updateProfile(userId(jwt), command));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update own profile; omitted/null fields retain their current values")
    public ResponseEntity<UserView> patch(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfilePatch command) {
        var current = identity.profile(userId(jwt));
        return profile(identity.updateProfile(current.id(), new ProfileChange(
                command.username() == null ? current.username() : command.username(),
                command.email() == null ? current.email() : command.email(),
                command.firstName() == null ? current.firstName() : command.firstName(),
                command.lastName() == null ? current.lastName() : command.lastName(), command.version())));
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "ADMIN changes a global role using current database rights and expected version",
            description = "Demoting the last ADMIN is rejected with HTTP 409 and code LAST_ADMIN, "
                    + "including concurrent role changes.")
    public ResponseEntity<UserView> role(@AuthenticationPrincipal Jwt jwt, @Positive @PathVariable long id,
            @Valid @RequestBody RoleChange command) {
        return profile(identity.changeRole(userId(jwt), id, command.role(), command.version()));
    }

    @PostMapping("/me/password")
    @Operation(summary = "Change password with current password; revoke every session immediately")
    @ApiResponse(responseCode = "204", description = "Password changed and all sessions revoked")
    public ResponseEntity<Void> password(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChange command) {
        identity.changePassword(userId(jwt), command.currentPassword(), command.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private static ResponseEntity<UserView> profile(UserView user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(user);
    }
}

package com.example.taskmanagement.security.web;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.shared.validation.NoNul;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record LoginRequest(@NotBlank @Size(max = 254) @NoNul String identifier,
            @NotBlank @Size(max = 128) String password) {
        @Override
        public String toString() {
            return "LoginRequest[credentials redacted]";
        }
    }

    public record RefreshRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String refreshToken) {
        @Override
        public String toString() {
            return "RefreshRequest[credentials redacted]";
        }
    }

    private final IdentityOperations identity;
    private final TokenService tokens;

    public AuthController(IdentityOperations identity, TokenService tokens) {
        this.identity = identity;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a USER; client-supplied roles cannot grant privileges")
    @ApiResponse(responseCode = "201", description = "USER registered")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterUser command) {
        return ResponseEntity.created(URI.create("/api/users/me")).cacheControl(CacheControl.noStore())
                .body(identity.register(command));
    }

    @PostMapping("/login")
    @Operation(summary = "Login using email or username (case insensitive)")
    public ResponseEntity<TokenService.Tokens> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(tokens.issue(identity.login(request.identifier(), request.password())));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Consume a refresh token and rotate it without extending the seven-day session")
    public ResponseEntity<TokenService.Tokens> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(tokens.issue(identity.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current session and its access/refresh tokens")
    @ApiResponse(responseCode = "204", description = "Session revoked")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        identity.logout(Long.parseLong(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}

package com.example.taskmanagement.integration.email.web;

import com.example.taskmanagement.integration.email.api.EmailOperations;
import com.example.taskmanagement.integration.email.api.EmailOperations.EmailChannelStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
@SecurityRequirement(name = "bearerAuth")
public class EmailController {
    public record EnabledChange(@NotNull Boolean enabled) {
    }

    private final EmailOperations email;

    public EmailController(EmailOperations email) {
        this.email = email;
    }

    @GetMapping
    @Operation(summary = "Email channel status; drains pending email outbox")
    public ResponseEntity<EmailChannelStatus> status(@AuthenticationPrincipal Jwt jwt) {
        email.deliverPending(50);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(email.status(actor(jwt)));
    }

    @PutMapping("/enabled")
    @Operation(summary = "Mute or unmute email deliveries for the current user")
    public ResponseEntity<EmailChannelStatus> enabled(@AuthenticationPrincipal Jwt jwt,
            @RequestBody EnabledChange command) {
        email.setEnabled(actor(jwt), Boolean.TRUE.equals(command.enabled()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(email.status(actor(jwt)));
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

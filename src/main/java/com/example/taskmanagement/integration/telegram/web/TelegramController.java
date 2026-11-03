package com.example.taskmanagement.integration.telegram.web;

import com.example.taskmanagement.integration.telegram.api.TelegramOperations;
import com.example.taskmanagement.integration.telegram.api.TelegramOperations.LinkResponse;
import com.example.taskmanagement.integration.telegram.api.TelegramOperations.TelegramChannelStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telegram")
public class TelegramController {
    public record EnabledChange(@NotNull Boolean enabled) {
    }

    private final TelegramOperations telegram;

    public TelegramController(TelegramOperations telegram) {
        this.telegram = telegram;
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Telegram channel status for the current user; drains pending Telegram outbox")
    public ResponseEntity<TelegramChannelStatus> status(@AuthenticationPrincipal Jwt jwt) {
        telegram.deliverPending(50);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(telegram.status(actor(jwt)));
    }

    @PostMapping("/link")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a one-time deep-link token to bind Telegram chat")
    public ResponseEntity<LinkResponse> link(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(telegram.createLink(actor(jwt)));
    }

    @PutMapping("/enabled")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Mute or unmute Telegram deliveries for the current user")
    public ResponseEntity<TelegramChannelStatus> enabled(@AuthenticationPrincipal Jwt jwt,
            @RequestBody EnabledChange command) {
        telegram.setEnabled(actor(jwt), Boolean.TRUE.equals(command.enabled()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(telegram.status(actor(jwt)));
    }

    @DeleteMapping("/link")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Unlink Telegram chat from the current user")
    public ResponseEntity<TelegramChannelStatus> unlink(@AuthenticationPrincipal Jwt jwt) {
        telegram.unlink(actor(jwt));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(telegram.status(actor(jwt)));
    }

    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Telegram Bot API webhook for /start <token>")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody String body) {
        telegram.handleUpdate(secret, body);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

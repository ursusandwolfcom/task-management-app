package com.example.taskmanagement.notification.web;

import com.example.taskmanagement.notification.api.NotificationOperations;
import com.example.taskmanagement.notification.api.NotificationOperations.NotificationPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {
    private final NotificationOperations notifications;

    public NotificationController(NotificationOperations notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "List in-app notifications for the current user; drains outbox first")
    public ResponseEntity<NotificationPage> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        notifications.projectPending(50);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(notifications.list(actor(jwt), unreadOnly, page, size));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        notifications.markRead(actor(jwt), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications read for the current user")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notifications.markAllRead(actor(jwt));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

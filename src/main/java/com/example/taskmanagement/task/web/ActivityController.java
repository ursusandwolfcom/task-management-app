package com.example.taskmanagement.task.web;

import com.example.taskmanagement.task.api.ActivityOperations;
import com.example.taskmanagement.task.api.ActivityOperations.ActivityPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class ActivityController {
    private final ActivityOperations activities;

    public ActivityController(ActivityOperations activities) {
        this.activities = activities;
    }

    @GetMapping("/api/tasks/{id}/activity")
    @Operation(summary = "Read append-only task activity history, newest first; paginated")
    public ResponseEntity<ActivityPage> list(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(activities.list(actor(jwt), id, page, size));
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

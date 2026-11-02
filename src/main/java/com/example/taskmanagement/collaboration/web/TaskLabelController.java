package com.example.taskmanagement.collaboration.web;

import com.example.taskmanagement.collaboration.api.LabelOperations;
import com.example.taskmanagement.collaboration.api.LabelOperations.LabelPage;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/{taskId}/labels")
@SecurityRequirement(name = "bearerAuth")
public class TaskLabelController {
    private final LabelOperations labels;

    public TaskLabelController(LabelOperations labels) {
        this.labels = labels;
    }

    @GetMapping
    public ResponseEntity<LabelPage> list(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long taskId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(labels.taskLabels(actor(jwt), taskId, page, size));
    }

    @PutMapping("/{labelId}")
    @ApiResponse(responseCode = "204", description = "Task-label association present; repeated calls are idempotent")
    public ResponseEntity<Void> link(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long taskId,
            @PathVariable @Positive long labelId) {
        labels.link(actor(jwt), taskId, labelId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/{labelId}")
    @ApiResponse(responseCode = "204", description = "Task-label association absent; repeated calls are idempotent")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long taskId,
            @PathVariable @Positive long labelId) {
        labels.unlink(actor(jwt), taskId, labelId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

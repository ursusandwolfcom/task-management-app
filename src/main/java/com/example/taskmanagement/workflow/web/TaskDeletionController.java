package com.example.taskmanagement.workflow.web;

import com.example.taskmanagement.task.api.TaskOperations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class TaskDeletionController {
    private final TaskOperations tasks;

    public TaskDeletionController(TaskOperations tasks) {
        this.tasks = tasks;
    }

    @DeleteMapping("/api/tasks/{id}")
    @Operation(summary = "Owner or ADMIN deletes only never-started task without dependents",
            description = "Expected version required. History-bearing tasks remain after retry; "
                    + "incoming dependencies are removed atomically for a deletable task.")
    @ApiResponse(responseCode = "204", description = "Task removed; no execution history was discarded")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @RequestParam @PositiveOrZero long version) {
        tasks.delete(Long.parseLong(jwt.getSubject()), id, version);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}

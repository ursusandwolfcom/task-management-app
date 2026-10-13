package com.example.taskmanagement.workflow.web;

import com.example.taskmanagement.workflow.application.ProjectDeletionService;
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
public class ProjectDeletionController {
    private final ProjectDeletionService deletion;

    public ProjectDeletionController(ProjectDeletionService deletion) {
        this.deletion = deletion;
    }

    @DeleteMapping("/api/projects/{id}")
    @Operation(summary = "Owner or ADMIN deletes an empty project with expected version",
            description = "Only memberships cascade. Task/file deletion is a later workflow feature.")
    @ApiResponse(responseCode = "204", description = "Project deleted")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @Positive @PathVariable long id,
            @PositiveOrZero @RequestParam long version) {
        deletion.delete(Long.parseLong(jwt.getSubject()), id, version);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}

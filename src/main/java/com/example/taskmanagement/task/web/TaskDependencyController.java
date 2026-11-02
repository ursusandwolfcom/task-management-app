package com.example.taskmanagement.task.web;

import com.example.taskmanagement.task.api.TaskDependencyOperations;
import com.example.taskmanagement.task.api.TaskDependencyOperations.Change;
import com.example.taskmanagement.task.api.TaskDependencyOperations.Predecessors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/tasks/{id}/dependencies")
@SecurityRequirement(name = "bearerAuth")
public class TaskDependencyController {
    private final TaskDependencyOperations dependencies;

    public TaskDependencyController(TaskDependencyOperations dependencies) {
        this.dependencies = dependencies;
    }

    @GetMapping
    @Operation(summary = "List incoming predecessor IDs for an accessible task",
            description = "Returns the coherent project inputRevision; does not change the graph.")
    public ResponseEntity<Predecessors> list(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        var listed = dependencies.list(actor(jwt), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Project-Input-Revision", Long.toString(listed.inputRevision())).body(listed);
    }

    @PostMapping
    @Operation(summary = "Add a finish-to-start predecessor; participant, owner or ADMIN",
            description = "Body: dependsOnTaskId and expected project inputRevision. Only NOT_STARTED with no "
                    + "attempts may change dependencies. Same-project/self/duplicate/cycle guards run under "
                    + "the shared project lock. A successful change increments inputRevision once.")
    public ResponseEntity<Change> add(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @RequestBody JsonNode body) {
        TaskBody.validate(body, Set.of("dependsOnTaskId", "inputRevision"));
        long predecessor = TaskBody.requiredNumber(body, "dependsOnTaskId", 1);
        long revision = TaskBody.requiredNumber(body, "inputRevision", 0);
        var changed = dependencies.add(actor(jwt), id, predecessor, revision);
        return ResponseEntity.created(URI.create("/api/tasks/" + id + "/dependencies/" + predecessor))
                .cacheControl(CacheControl.noStore())
                .header("X-Project-Input-Revision", Long.toString(changed.inputRevision())).body(changed);
    }

    @DeleteMapping("/{dependsOnTaskId}")
    @Operation(summary = "Remove an incoming dependency from a never-started NOT_STARTED task",
            description = "inputRevision is the expected project graph revision. Missing edge: DEPENDENCY_NOT_FOUND. "
                    + "Removal can reduce an oversized graph; execution history is never rewritten.")
    @ApiResponse(responseCode = "204", description = "Dependency removed; new X-Project-Input-Revision supplied")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @PathVariable @Positive long dependsOnTaskId, @RequestParam @PositiveOrZero long inputRevision) {
        var changed = dependencies.remove(actor(jwt), id, dependsOnTaskId, inputRevision);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header("X-Project-Input-Revision", Long.toString(changed.inputRevision())).build();
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

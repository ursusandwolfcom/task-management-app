package com.example.taskmanagement.task.web;

import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskOperations.AttemptPage;
import com.example.taskmanagement.task.api.TaskOperations.TaskPage;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy.Command;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.Set;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class TaskController {
    private final TaskOperations tasks;

    public TaskController(TaskOperations tasks) {
        this.tasks = tasks;
    }

    @PostMapping({"/api/tasks", "/api/projects/{projectId}/tasks"})
    @Operation(summary = "Create NOT_STARTED task; participant or ADMIN",
            description = "Body fields: projectId (required on /api/tasks), name, description, priority, "
                    + "estimatedMinutes (optional integer 15..525600), dueDate, assigneeId. "
                    + "An assignee must be a project member. Unknown/lifecycle fields are rejected.")
    public ResponseEntity<TaskView> create(@AuthenticationPrincipal Jwt jwt,
            @PathVariable(required = false) @Positive Long projectId, @RequestBody JsonNode body) {
        long resolved = TaskBody.projectId(body, projectId);
        if (body.has("version")) {
            throw new com.example.taskmanagement.task.api.TaskException(
                    com.example.taskmanagement.task.api.TaskException.Reason.VALIDATION_ERROR);
        }
        TaskData data = TaskBody.data(body, null);
        var task = tasks.create(actor(jwt), resolved, data);
        return ResponseEntity.created(URI.create("/api/tasks/" + task.id()))
                .cacheControl(CacheControl.noStore()).body(task);
    }

    @GetMapping({"/api/tasks", "/api/projects/{projectId}/tasks"})
    @Operation(summary = "List project tasks, newest first; page 0..1000000, size 1..100",
            description = "projectId is required as a query parameter on /api/tasks. "
                    + "Conflicting path/query project IDs are rejected.")
    public ResponseEntity<TaskPage> list(@AuthenticationPrincipal Jwt jwt,
            @PathVariable(required = false) @Positive Long projectId,
            @RequestParam(name = "projectId", required = false) @Positive Long filter,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if ((projectId == null && filter == null)
                || (projectId != null && filter != null && !projectId.equals(filter))) {
            throw new com.example.taskmanagement.task.api.TaskException(
                    com.example.taskmanagement.task.api.TaskException.Reason.VALIDATION_ERROR);
        }
        return response(tasks.list(actor(jwt), projectId == null ? filter : projectId, page, size));
    }

    @GetMapping("/api/tasks/{id}")
    @Operation(summary = "Read accessible task; missing and inaccessible tasks return TASK_NOT_FOUND")
    public ResponseEntity<TaskView> read(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        return response(tasks.read(actor(jwt), id));
    }

    @PutMapping("/api/tasks/{id}")
    @Operation(summary = "Replace task metadata with expected version; projectId is immutable",
            description = "name and priority required. Omitted optional fields are cleared. "
                    + "status/factual dates/history cannot be supplied. Assignee changes only NOT_STARTED/FAILED.")
    public ResponseEntity<TaskView> replace(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @RequestBody JsonNode body) {
        var previous = tasks.read(actor(jwt), id);
        TaskBody.immutableProject(body, previous.projectId());
        return response(tasks.replace(actor(jwt), id, TaskBody.data(body, null), TaskBody.version(body)));
    }

    @PatchMapping("/api/tasks/{id}")
    @Operation(summary = "Patch task metadata with expected version",
            description = "Omitted fields retain values; null clears description, estimate, dueDate or assignee. "
                    + "name/priority cannot be null. Same invariants as PUT; command fields rejected.")
    public ResponseEntity<TaskView> patch(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @RequestBody JsonNode body) {
        var previous = tasks.read(actor(jwt), id);
        TaskBody.immutableProject(body, previous.projectId());
        return response(tasks.replace(actor(jwt), id, TaskBody.data(body, previous), TaskBody.version(body)));
    }

    @PostMapping({"/api/tasks/{id}/start", "/api/tasks/{id}/complete", "/api/tasks/{id}/fail",
            "/api/tasks/{id}/cancel", "/api/tasks/{id}/retry"})
    @Operation(summary = "Execute a lifecycle command with body {version}",
            description = "start/complete/fail/retry: assignee, owner or ADMIN; cancel: owner or ADMIN. "
                    + "Start requires assignment and every actual predecessor COMPLETED. "
                    + "Retry retains all attempts. No resource-calendar guard yet.")
    public ResponseEntity<TaskView> execute(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @RequestBody JsonNode body, jakarta.servlet.http.HttpServletRequest request) {
        TaskBody.validate(body, Set.of("version"));
        String path = request.getRequestURI();
        var command = Command.valueOf(path.substring(path.lastIndexOf('/') + 1).toUpperCase(java.util.Locale.ROOT));
        return response(tasks.execute(actor(jwt), id, command, TaskBody.version(body)));
    }

    @GetMapping("/api/tasks/{id}/attempts")
    @Operation(summary = "Read immutable execution history, newest attempt first; paginated")
    public ResponseEntity<AttemptPage> attempts(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return response(tasks.attempts(actor(jwt), id, page, size));
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}

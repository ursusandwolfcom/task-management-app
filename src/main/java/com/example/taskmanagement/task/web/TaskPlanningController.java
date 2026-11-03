package com.example.taskmanagement.task.web;

import com.example.taskmanagement.task.api.TaskPlanningOperations;
import com.example.taskmanagement.task.api.TaskPlanningOperations.AvailableTasks;
import com.example.taskmanagement.task.api.TaskPlanningOperations.ExecutionOrder;
import com.example.taskmanagement.task.api.TaskPlanningOperations.NextTask;
import com.example.taskmanagement.task.api.TaskPlanningOperations.ProjectGantt;
import com.example.taskmanagement.task.api.TaskPlanningOperations.WhatIfRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{id}")
@SecurityRequirement(name = "bearerAuth")
public class TaskPlanningController {
    private static final String REVISION = "X-Project-Input-Revision";
    private final TaskPlanningOperations planning;

    public TaskPlanningController(TaskPlanningOperations planning) {
        this.planning = planning;
    }

    @GetMapping("/execution-order")
    @Operation(summary = "Order the full persisted project graph with the Java planning core",
            description = "Snapshot includes inputRevision; GET never changes execution facts. "
                    + "Priority desc, dueDate asc/null last, ID asc; dependencies take precedence. "
                    + "Oversized graphs return GRAPH_TOO_LARGE; no partial graph is calculated.")
    public ResponseEntity<ExecutionOrder> executionOrder(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long id) {
        var result = planning.executionOrder(actor(jwt), id);
        return response(result, result.inputRevision());
    }

    @GetMapping("/available-tasks")
    @Operation(summary = "Read NOT_STARTED tasks whose actual predecessors are all COMPLETED",
            description = "Includes blockingReasons for blocked NOT_STARTED tasks. FAILED/CANCELLED predecessors "
                    + "remain blockers. Readiness does not grant execution rights or reserve resources/assignees.")
    public ResponseEntity<AvailableTasks> availableTasks(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long id) {
        var result = planning.availableTasks(actor(jwt), id);
        return response(result, result.inputRevision());
    }

    @GetMapping("/next-task")
    @Operation(summary = "Recommend the first available task without starting or reserving it")
    @ApiResponse(responseCode = "200", description = "Available task with the coherent project inputRevision")
    @ApiResponse(responseCode = "204", description = "No available task; X-Project-Input-Revision still supplied")
    public ResponseEntity<NextTask> nextTask(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        var result = planning.nextTask(actor(jwt), id);
        if (result.task() == null) {
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                    .header(REVISION, Long.toString(result.inputRevision())).build();
        }
        return response(result, result.inputRevision());
    }

    @GetMapping("/gantt")
    @Operation(summary = "Calculate working-time CPM / Gantt bars for the project graph",
            description = "Mon–Fri 09:00–17:00 with lunch 13:00–14:00 in the project timezone. "
                    + "Missing estimates are zero-length milestones. Unlimited parallel resources; "
                    + "no resource leveling. GET never mutates tasks; "
                    + "X-Project-Input-Revision mirrors the snapshot.")
    public ResponseEntity<ProjectGantt> gantt(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        var result = planning.gantt(actor(jwt), id);
        return response(result, result.inputRevision());
    }

    @PostMapping("/gantt/what-if")
    @Operation(summary = "What-if CPM with estimate overrides; does not save tasks",
            description = "Same calendar rules as /gantt. Unknown taskId → 404 TASK_NOT_FOUND; "
                    + "duplicate override → 400 VALIDATION_ERROR. Persisted estimates stay unchanged.")
    public ResponseEntity<ProjectGantt> whatIf(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @Valid @RequestBody WhatIfRequest body) {
        var result = planning.whatIf(actor(jwt), id, body);
        return response(result, result.inputRevision());
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private static <T> ResponseEntity<T> response(T body, long revision) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(REVISION, Long.toString(revision)).body(body);
    }
}

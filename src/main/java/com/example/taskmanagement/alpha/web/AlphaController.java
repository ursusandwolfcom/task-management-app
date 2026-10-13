package com.example.taskmanagement.alpha.web;

import com.example.taskmanagement.planning.core.DependencyEdge;
import com.example.taskmanagement.planning.core.ExecutionFacts;
import com.example.taskmanagement.planning.core.GraphSnapshot;
import com.example.taskmanagement.planning.core.PriorityTopologicalSorter;
import com.example.taskmanagement.planning.core.ReadinessCalculator;
import com.example.taskmanagement.planning.core.TaskNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Local alpha adapter: serves the prototype and calculates supplied graphs without database mutations. */
@Controller
@Profile("alpha")
@RequestMapping("/alpha")
public class AlphaController {

    private static final long MAX_BROWSER_INTEGER = 9007199254740991L;

    @GetMapping(value = {"", "/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> page() {
        return asset("index.html", MediaType.TEXT_HTML);
    }

    @GetMapping(value = "/styles.css", produces = "text/css")
    public ResponseEntity<Resource> styles() {
        return asset("styles.css", MediaType.parseMediaType("text/css"));
    }

    @GetMapping(value = "/app.js", produces = "text/javascript")
    public ResponseEntity<Resource> appScript() {
        return asset("app.js", MediaType.parseMediaType("text/javascript"));
    }

    @GetMapping(value = "/store.js", produces = "text/javascript")
    public ResponseEntity<Resource> storeScript() {
        return asset("store.js", MediaType.parseMediaType("text/javascript"));
    }

    @GetMapping(value = "/ui.js", produces = "text/javascript")
    public ResponseEntity<Resource> uiScript() {
        return asset("ui.js", MediaType.parseMediaType("text/javascript"));
    }

    @GetMapping(value = "/demo.js", produces = "text/javascript")
    public ResponseEntity<Resource> demoScript() {
        return asset("demo.js", MediaType.parseMediaType("text/javascript"));
    }

    @PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Operation(summary = "Calculate dependency order and actual readiness",
            description = "Pure calculation over the supplied graph; no tasks are written to PostgreSQL. "
                    + "Only completedIds satisfy dependencies; candidateIds are not-started tasks.")
    @ApiResponse(responseCode = "200", description = "Order, available task IDs and nullable next task",
            content = @Content(schema = @Schema(implementation = PlanResponse.class)))
    @ApiResponse(responseCode = "400", description = "Malformed input, constraint violation or invalid graph",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(ref = "#/components/schemas/ApiError")))
    @ApiResponse(responseCode = "409", description = "Cycle; unresolved IDs can include blocked descendants",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(ref = "#/components/schemas/ApiError"),
                    examples = @ExampleObject(value = """
                            {"type":"about:blank","title":"Conflict","status":409,
                             "detail":"Graph contains a cycle","code":"CYCLIC_DEPENDENCY","taskIds":[1,2]}
                            """)))
    public ResponseEntity<PlanResponse> plan(@Valid @RequestBody PlanRequest request) {
        var nodes = request.tasks().stream().map(task -> new TaskNode(task.id(), task.priority().weight,
                task.dueDate(), task.estimatedMinutes())).toList();
        var edges = request.dependencies().stream()
                .map(edge -> new DependencyEdge(edge.predecessorId(), edge.successorId())).toList();
        var graph = GraphSnapshot.of(nodes, edges);
        var facts = new ExecutionFacts(request.completedIds(), request.candidateIds());
        var sorter = new PriorityTopologicalSorter();
        var order = sorter.executionOrder(graph).stream().map(TaskNode::id).toList();
        var available = new ReadinessCalculator(sorter).availableTasks(graph, facts)
                .stream().map(TaskNode::id).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new PlanResponse(order, available, available.isEmpty() ? null : available.getFirst()));
    }

    private static ResponseEntity<Resource> asset(String filename, MediaType mediaType) {
        return ResponseEntity.ok().contentType(mediaType).cacheControl(CacheControl.noStore())
                .body(new ClassPathResource("alpha/" + filename));
    }

    public enum Priority {
        LOW(10), MEDIUM(20), HIGH(30), CRITICAL(40);

        private final int weight;

        Priority(int weight) {
            this.weight = weight;
        }
    }

    public record RequestTask(@Positive @Max(MAX_BROWSER_INTEGER) long id, @NotNull Priority priority,
            LocalDate dueDate, @Positive @Max(MAX_BROWSER_INTEGER) Long estimatedMinutes) {
    }

    public record RequestDependency(@Positive @Max(MAX_BROWSER_INTEGER) long predecessorId,
            @Positive @Max(MAX_BROWSER_INTEGER) long successorId) {
    }

    public record PlanRequest(@NotNull @Size(max = 1000) List<@NotNull @Valid RequestTask> tasks,
            @NotNull @Size(max = 10000) List<@NotNull @Valid RequestDependency> dependencies,
            @NotNull @Size(max = 1000) Set<@NotNull @Positive @Max(MAX_BROWSER_INTEGER) Long> completedIds,
            @NotNull @Size(max = 1000) Set<@NotNull @Positive @Max(MAX_BROWSER_INTEGER) Long> candidateIds) {
    }

    public record PlanResponse(List<Long> executionOrder, List<Long> availableIds, Long nextId) {
    }

}

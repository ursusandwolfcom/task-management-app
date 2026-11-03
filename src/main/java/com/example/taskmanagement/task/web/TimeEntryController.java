package com.example.taskmanagement.task.web;

import com.example.taskmanagement.shared.web.JsonFields;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.api.TimeEntryData;
import com.example.taskmanagement.task.api.TimeEntryOperations;
import com.example.taskmanagement.task.api.TimeEntryOperations.TimeEntryPage;
import com.example.taskmanagement.task.api.TimeEntryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
@RequestMapping("/api/time-entries")
@SecurityRequirement(name = "bearerAuth")
public class TimeEntryController {
    private final TimeEntryOperations entries;

    public TimeEntryController(TimeEntryOperations entries) {
        this.entries = entries;
    }

    @PostMapping
    @Operation(summary = "Log actual effort against an accessible task; user and UTC timestamp are server-owned")
    @ApiResponse(responseCode = "201", description = "Time entry created without changing planning revisions")
    public ResponseEntity<TimeEntryView> create(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        var fields = new JsonFields(body, () -> new TaskException(TaskException.Reason.VALIDATION_ERROR))
                .only(Set.of("taskId", "workDate", "minutes", "note"));
        long taskId = fields.requiredNumber("taskId", 1);
        String workDateText = fields.text("workDate", null);
        if (workDateText == null || workDateText.isBlank()) {
            throw new TaskException(TaskException.Reason.VALIDATION_ERROR);
        }
        LocalDate workDate;
        try {
            workDate = LocalDate.parse(workDateText.strip());
        } catch (DateTimeParseException ex) {
            throw new TaskException(TaskException.Reason.VALIDATION_ERROR);
        }
        int minutes = Math.toIntExact(fields.requiredNumber("minutes", 1));
        if (minutes > 1440) {
            throw new TaskException(TaskException.Reason.VALIDATION_ERROR);
        }
        String note = fields.text("note", null);
        var created = entries.create(actor(jwt), new TimeEntryData(taskId, workDate, minutes, note));
        return ResponseEntity.created(URI.create("/api/time-entries/" + created.id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }

    @GetMapping
    @Operation(summary = "List task time entries newest first; includes totalMinutes across all pages")
    public ResponseEntity<TimeEntryPage> list(@AuthenticationPrincipal Jwt jwt, @RequestParam @Positive long taskId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(entries.list(actor(jwt), taskId, page, size));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete time entry as its author, current project owner or ADMIN")
    @ApiResponse(responseCode = "204", description = "Time entry removed without changing planning revisions")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        entries.delete(actor(jwt), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

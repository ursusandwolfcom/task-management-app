package com.example.taskmanagement.project.web;

import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectOperations.MemberPage;
import com.example.taskmanagement.project.api.ProjectOperations.ProjectPage;
import com.example.taskmanagement.project.api.ProjectPatch;
import com.example.taskmanagement.project.api.ProjectView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {
    public record ChangeRequest(String name, String description, LocalDate startDate, LocalDate endDate,
            String timezone, @NotNull @PositiveOrZero Long version) {
    }

    public record VersionRequest(@NotNull @PositiveOrZero Long version) {
    }

    public record OwnerRequest(@Positive long userId, @NotNull @PositiveOrZero Long version) {
    }

    private final ProjectOperations projects;

    public ProjectController(ProjectOperations projects) {
        this.projects = projects;
    }

    @PostMapping
    @Operation(summary = "Create INITIATED project; authenticated creator is owner and member")
    @ApiResponse(responseCode = "201", description = "Project and owner membership created atomically")
    public ResponseEntity<ProjectView> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProjectData data) {
        var created = projects.create(actor(jwt), data);
        return ResponseEntity.created(URI.create("/api/projects/" + created.id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }

    @GetMapping
    @Operation(summary = "List accessible projects, newest first; ADMIN sees all",
            description = "page >= 0 (maximum 1000000), size 1..100, defaults 0 and 20")
    public ResponseEntity<ProjectPage> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return response(projects.list(actor(jwt), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read project; missing and inaccessible projects both return 404")
    public ResponseEntity<ProjectView> read(@AuthenticationPrincipal Jwt jwt, @Positive @PathVariable long id) {
        return response(projects.read(actor(jwt), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Owner or ADMIN replaces metadata with expected version; status and owner are not writable")
    public ResponseEntity<ProjectView> replace(@AuthenticationPrincipal Jwt jwt, @Positive @PathVariable long id,
            @Valid @RequestBody ChangeRequest request) {
        return response(projects.replace(actor(jwt), id, new ProjectData(request.name(), request.description(),
                request.startDate(), request.endDate(), request.timezone()), request.version()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Owner or ADMIN patches metadata; omitted/null fields retain values under the project lock")
    public ResponseEntity<ProjectView> patch(@AuthenticationPrincipal Jwt jwt, @Positive @PathVariable long id,
            @Valid @RequestBody ChangeRequest request) {
        return response(projects.patch(actor(jwt), id, new ProjectPatch(request.name(), request.description(),
                request.startDate(), request.endDate(), request.timezone()), request.version()));
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "List member IDs and owner flags; same pagination as projects")
    public ResponseEntity<MemberPage> members(@AuthenticationPrincipal Jwt jwt, @Positive @PathVariable long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return response(projects.members(actor(jwt), id, page, size));
    }

    @PutMapping("/{id}/members/{userId}")
    @Operation(summary = "Owner or ADMIN adds an existing user; duplicate membership returns 409")
    public ResponseEntity<ProjectView> add(@AuthenticationPrincipal Jwt jwt, @Positive @PathVariable long id,
            @Positive @PathVariable long userId, @Valid @RequestBody VersionRequest request) {
        return response(projects.addMember(actor(jwt), id, userId, request.version()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Owner or ADMIN removes a member; current owner cannot be removed")
    public ResponseEntity<ProjectView> remove(@AuthenticationPrincipal Jwt jwt, @Positive @PathVariable long id,
            @Positive @PathVariable long userId, @PositiveOrZero @RequestParam long version) {
        return response(projects.removeMember(actor(jwt), id, userId, version));
    }

    @PutMapping("/{id}/owner")
    @Operation(summary = "Owner or ADMIN transfers ownership to an existing member",
            description = "Previous owner remains a member and loses owner rights. Expected version is required.")
    public ResponseEntity<ProjectView> owner(@AuthenticationPrincipal Jwt jwt, @Positive @PathVariable long id,
            @Valid @RequestBody OwnerRequest request) {
        return response(projects.transferOwner(actor(jwt), id, request.userId(), request.version()));
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}

package com.example.taskmanagement.collaboration.web;

import com.example.taskmanagement.collaboration.api.CollaborationException;
import com.example.taskmanagement.collaboration.api.CommentData;
import com.example.taskmanagement.collaboration.api.CommentOperations;
import com.example.taskmanagement.collaboration.api.CommentOperations.CommentPage;
import com.example.taskmanagement.collaboration.api.CommentView;
import com.example.taskmanagement.shared.web.JsonFields;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/api/comments")
@SecurityRequirement(name = "bearerAuth")
public class CommentController {
    private final CommentOperations comments;

    public CommentController(CommentOperations comments) {
        this.comments = comments;
    }

    @PostMapping
    @Operation(summary = "Add plain-text comment to an accessible task; author and UTC timestamp are server-owned")
    @ApiResponse(responseCode = "201", description = "Comment created without changing task or planning revisions")
    public ResponseEntity<CommentView> create(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        var fields = new JsonFields(body,
                () -> new CollaborationException(CollaborationException.Reason.VALIDATION_ERROR))
                .only(Set.of("taskId", "text"));
        var created = comments.create(actor(jwt), new CommentData(fields.requiredNumber("taskId", 1),
                fields.text("text", null)));
        return ResponseEntity.created(URI.create("/api/comments/" + created.id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }

    @GetMapping
    @Operation(summary = "List task comments newest first; page 0..1000000, size 1..100, defaults 0/20")
    public ResponseEntity<CommentPage> list(@AuthenticationPrincipal Jwt jwt, @RequestParam @Positive long taskId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(comments.list(actor(jwt), taskId, page, size));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete comment as its author, current project owner or ADMIN; current access required")
    @ApiResponse(responseCode = "204", description = "Comment removed without changing planning revisions")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        comments.delete(actor(jwt), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

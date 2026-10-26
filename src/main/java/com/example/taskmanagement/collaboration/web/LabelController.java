package com.example.taskmanagement.collaboration.web;

import com.example.taskmanagement.collaboration.api.CollaborationException;
import com.example.taskmanagement.collaboration.api.LabelData;
import com.example.taskmanagement.collaboration.api.LabelOperations;
import com.example.taskmanagement.collaboration.api.LabelOperations.LabelPage;
import com.example.taskmanagement.collaboration.api.LabelView;
import com.example.taskmanagement.shared.web.JsonFields;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/labels")
@SecurityRequirement(name = "bearerAuth")
public class LabelController {
    private final LabelOperations labels;

    public LabelController(LabelOperations labels) {
        this.labels = labels;
    }

    @PostMapping
    public ResponseEntity<LabelView> create(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        var fields = fields(body).only(Set.of("projectId", "name", "color"));
        var label = labels.create(actor(jwt), fields.requiredNumber("projectId", 1), data(fields));
        return ResponseEntity.created(URI.create("/api/labels/" + label.id()))
                .cacheControl(CacheControl.noStore()).body(label);
    }

    @GetMapping
    public ResponseEntity<LabelPage> list(@AuthenticationPrincipal Jwt jwt, @RequestParam @Positive long projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(labels.list(actor(jwt), projectId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LabelView> read(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(labels.read(actor(jwt), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LabelView> replace(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @RequestBody JsonNode body) {
        var fields = fields(body).only(Set.of("name", "color", "version"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(labels.replace(actor(jwt), id, data(fields), fields.requiredNumber("version", 0)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @RequestParam @PositiveOrZero long version) {
        labels.delete(actor(jwt), id, version);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static LabelData data(JsonFields fields) {
        return new LabelData(fields.text("name", null), fields.text("color", null));
    }

    private static JsonFields fields(JsonNode body) {
        return new JsonFields(body, () -> new CollaborationException(CollaborationException.Reason.VALIDATION_ERROR));
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

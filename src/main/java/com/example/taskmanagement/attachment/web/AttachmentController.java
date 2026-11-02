package com.example.taskmanagement.attachment.web;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.AttachmentOperations;
import com.example.taskmanagement.attachment.api.AttachmentOperations.AttachmentPage;
import com.example.taskmanagement.attachment.api.AttachmentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/attachments")
@SecurityRequirement(name = "bearerAuth")
public class AttachmentController {
    private final AttachmentOperations attachments;

    public AttachmentController(AttachmentOperations attachments) {
        this.attachments = attachments;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a task attachment (max 5 MiB; pdf/png/jpeg/webp/plain text)")
    @ApiResponse(responseCode = "201", description = "Attachment stored without changing task or planning revisions")
    public ResponseEntity<AttachmentView> upload(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @Positive long taskId, @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
        }
        try {
            var created = attachments.upload(actor(jwt), taskId, file.getOriginalFilename(), file.getContentType(),
                    file.getSize(), file.getInputStream());
            return ResponseEntity.created(URI.create("/api/attachments/" + created.id()))
                    .cacheControl(CacheControl.noStore()).body(created);
        } catch (java.io.IOException exception) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
    }

    @GetMapping
    @Operation(summary = "List task attachments newest first; page 0..1000000, size 1..100, defaults 0/20")
    public ResponseEntity<AttachmentPage> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @Positive long taskId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(attachments.list(actor(jwt), taskId, page, size));
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Download attachment bytes for an accessible task")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long id) {
        var content = attachments.download(actor(jwt), id);
        var meta = content.metadata();
        String encoded = URLEncoder.encode(meta.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(meta.contentType()))
                .contentLength(meta.sizeBytes())
                .body(new InputStreamResource(content.body()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete attachment as uploader, current project owner or ADMIN")
    @ApiResponse(responseCode = "204", description = "Attachment removed without changing planning revisions")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        attachments.delete(actor(jwt), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

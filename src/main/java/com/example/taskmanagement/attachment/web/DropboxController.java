package com.example.taskmanagement.attachment.web;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.DropboxLinkOperations;
import com.example.taskmanagement.attachment.api.DropboxLinkOperations.AuthorizeResponse;
import com.example.taskmanagement.attachment.api.DropboxLinkOperations.DropboxChannelStatus;
import com.example.taskmanagement.shared.web.JsonFields;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/dropbox")
@SecurityRequirement(name = "bearerAuth")
public class DropboxController {
    private final DropboxLinkOperations dropbox;

    public DropboxController(DropboxLinkOperations dropbox) {
        this.dropbox = dropbox;
    }

    @GetMapping
    @Operation(summary = "Dropbox OAuth link status for the current user")
    public ResponseEntity<DropboxChannelStatus> status(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dropbox.status(actor(jwt)));
    }

    @PostMapping("/authorize")
    @Operation(summary = "Start Dropbox OAuth; returns authorizeUrl + signed state")
    public ResponseEntity<AuthorizeResponse> authorize(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dropbox.beginAuthorize(actor(jwt)));
    }

    @PostMapping("/callback")
    @Operation(summary = "Finish Dropbox OAuth with authorization code and state")
    public ResponseEntity<DropboxChannelStatus> callback(@AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode body) {
        var fields = new JsonFields(body, () -> new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR))
                .only(Set.of("code", "state"));
        String code = fields.text("code", null);
        String state = fields.text("state", null);
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(dropbox.completeAuthorize(actor(jwt), code, state));
    }

    @DeleteMapping("/link")
    @Operation(summary = "Unlink the current user's Dropbox account")
    public ResponseEntity<DropboxChannelStatus> unlink(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dropbox.unlink(actor(jwt)));
    }

    private static long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

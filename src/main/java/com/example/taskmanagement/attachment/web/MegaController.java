package com.example.taskmanagement.attachment.web;

import com.example.taskmanagement.attachment.api.MegaOperations;
import com.example.taskmanagement.attachment.api.MegaOperations.Status;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

@RestController
@RequestMapping("/api/mega")
@SecurityRequirement(name = "bearerAuth")
public class MegaController {
    public record Link(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password, @Pattern(regexp = "[0-9]{6}") String mfaCode) {
        @Override
        public String toString() {
            return "MegaLink[redacted]";
        }
    }

    private final MegaOperations mega;

    public MegaController(MegaOperations mega) {
        this.mega = mega;
    }

    @GetMapping
    @Operation(summary = "Read personal MEGA connection status; never returns session material")
    public ResponseEntity<Status> status(@AuthenticationPrincipal Jwt jwt) {
        return response(mega.status(Long.parseLong(jwt.getSubject())));
    }

    @PostMapping("/link")
    @Operation(summary = "Connect a personal MEGA account using the official native SDK",
            description = "Password/MFA are not persisted. The session is encrypted. "
                    + "Changing accounts leaves old attachments bound to their original account.")
    public ResponseEntity<Status> link(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Link request) {
        return response(mega.link(Long.parseLong(jwt.getSubject()), request.email(), request.password(),
                request.mfaCode()));
    }

    @DeleteMapping("/link")
    @Operation(summary = "Disconnect MEGA; keep metadata and remote files",
            description = "Existing MEGA files become unavailable until the same account is connected again. "
                    + "Local unlink does not revoke the session at MEGA.")
    public ResponseEntity<Status> unlink(@AuthenticationPrincipal Jwt jwt) {
        return response(mega.unlink(Long.parseLong(jwt.getSubject())));
    }

    private static ResponseEntity<Status> response(Status status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(status);
    }
}

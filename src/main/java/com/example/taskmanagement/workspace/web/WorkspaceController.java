package com.example.taskmanagement.workspace.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/** Serves only the workspace shell and compiled browser assets; business data stays behind /api security. */
@Controller
@Profile("workspace")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/app")
public class WorkspaceController {
    private static final String POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "connect-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; "
            + "frame-ancestors 'none'; form-action 'self'";

    @GetMapping(value = {"", "/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> page() {
        return asset("index.html", MediaType.TEXT_HTML);
    }

    @GetMapping("/assets/{filename}")
    public ResponseEntity<Resource> compiledAsset(@PathVariable String filename) {
        if (!filename.matches("[a-zA-Z0-9_-]+\\.(js|css)")) {
            return ResponseEntity.notFound().build();
        }
        var type = MediaType.parseMediaType(filename.endsWith(".css") ? "text/css" : "text/javascript");
        return asset("assets/" + filename, type);
    }

    private static ResponseEntity<Resource> asset(String name, MediaType type) {
        var resource = new ClassPathResource("workspace/" + name);
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", POLICY).body(resource);
    }
}

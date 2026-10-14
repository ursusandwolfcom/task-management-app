package com.example.taskmanagement.workspace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Profile isolation uses test resources; actual compiled UI is tested separately in a packaged browser. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "workspace"})
@Tag("integration")
class WorkspaceProfileIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private Environment environment;

    @Test
    void workspaceEnablesOnlyItsShellAndKeepsApiProtectedAndAlphaDocumentationDisabled() throws Exception {
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        mvc.perform(get("/app/")).andExpect(status().isOk());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        for (String path : new String[] {"/alpha/", "/alpha/app.js", "/v3/api-docs",
                "/swagger-ui/index.html", "/webjars/swagger-ui/index.html"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/alpha/plan")).andExpect(status().isNotFound());
    }
}

package com.example.taskmanagement.workspace.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Uses explicit test resources; browser acceptance must separately verify the actual compiled UI. */
class WorkspaceControllerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkspaceController()).build();

    @Test
    void servesShellAndCompiledResourcesWithoutCachingOrInlineScriptPermission() throws Exception {
        mvc.perform(get("/app/")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString(
                        "script-src 'self'")));
        mvc.perform(get("/app/assets/fixture-123.js")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));
        mvc.perform(get("/app/assets/fixture-123.css")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
    }

    @Test
    void sourceMapsAndUnknownFilesAreNeverServed() throws Exception {
        for (String path : new String[] {"/app/assets/fixture-123.js.map", "/app/assets/fixture.json",
                "/app/assets/missing.js", "/app/assets/application.properties", "/app/index.html"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }
}

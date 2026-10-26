package com.example.taskmanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class TaskManagementAppApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mvc;

    @Test
    void liquibaseCreatesApplicationSchema() {
        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'task_management'",
                Integer.class);
        assertEquals(1, schemaCount);

        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.databasechangelog WHERE id = ? AND exectype = 'EXECUTED'",
                Integer.class, "001-create-task-management-schema");
        assertEquals(1, migrationCount);
    }

    @Test
    void projectGraphIndexCoversProjectAndBothDependencyIdsWithPublicLiquibaseTracking() {
        var columns = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<java.util.List<String>>)
                connection -> {
                    var positions = new java.util.TreeMap<Short, String>();
                    try (var indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(),
                            "task_management", "task_dependencies", false, false)) {
                        while (indexes.next()) {
                            if ("dependencies_project_graph_idx".equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                                positions.put(indexes.getShort("ORDINAL_POSITION"),
                                        indexes.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
                            }
                        }
                    }
                    return java.util.List.copyOf(positions.values());
                });
        assertEquals(java.util.List.of("project_id", "task_id", "depends_on_task_id"), columns);
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from public.databasechangelog "
                + "where id = '005-index-project-graph' and exectype = 'EXECUTED'", Integer.class));
    }

    @Test
    void alphaPageAssetsAndPlanningEndpointAreDisabledByDefault() throws Exception {
        for (String path : new String[] {"/alpha/", "/alpha/styles.css", "/alpha/app.js", "/alpha/store.js",
                "/alpha/demo.js"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/alpha/plan")).andExpect(status().isNotFound());
    }

    @Test
    void openApiJsonYamlAndSwaggerAreDisabledWithoutAlpha() throws Exception {
        for (String path : new String[] {"/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config",
                "/v3/api-docs/session",
                "/swagger-ui.html", "/swagger-ui/index.html", "/webjars/swagger-ui/index.html",
                "/webjars/swagger-ui/swagger-ui.css"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    @Test
    void workspaceAssetsAreDisabledWithoutWorkspaceProfile() throws Exception {
        for (String path : new String[] {"/app/", "/app/assets/index.js", "/app/assets/index.css"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

}

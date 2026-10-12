package com.example.taskmanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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
    void alphaPageAssetsAndPlanningEndpointAreDisabledByDefault() throws Exception {
        for (String path : new String[] {"/alpha/", "/alpha/styles.css", "/alpha/app.js", "/alpha/store.js"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/alpha/plan")).andExpect(status().isNotFound());
    }

}

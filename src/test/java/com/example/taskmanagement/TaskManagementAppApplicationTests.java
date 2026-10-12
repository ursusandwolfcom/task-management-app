package com.example.taskmanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TaskManagementAppApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

}

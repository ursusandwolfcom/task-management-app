package com.example.taskmanagement.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Shared cleanup guard; test suites must never erase development or preview data. */
public final class DatabaseFixtures {
    private DatabaseFixtures() {
    }

    public static void clearIdentity(JdbcTemplate jdbc) {
        String database = jdbc.queryForObject("select current_catalog", String.class);
        if (database == null || !(database.startsWith("task_management_test_")
                || database.equals("task_management_ci"))) {
            throw new IllegalStateException("Integration tests require a dedicated task_management_test_* database.");
        }
        jdbc.update("delete from task_management.project_members");
        jdbc.update("delete from task_management.projects");
        jdbc.update("delete from task_management.identity_sessions");
        jdbc.update("delete from task_management.users");
    }
}

package com.example.taskmanagement.project.persistence;

import com.example.taskmanagement.project.api.ProjectBreakWindow;
import java.time.LocalTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectBreakRepository {
    private final JdbcTemplate jdbc;

    public ProjectBreakRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProjectBreakWindow> list(long projectId) {
        return jdbc.query(
                """
                select start_time, end_time from task_management.project_breaks
                where project_id = ? order by start_time
                """,
                (rs, rowNum) -> new ProjectBreakWindow(
                        rs.getObject(1, LocalTime.class),
                        rs.getObject(2, LocalTime.class)),
                projectId);
    }

    public void replace(long projectId, List<ProjectBreakWindow> breaks) {
        jdbc.update("delete from task_management.project_breaks where project_id = ?", projectId);
        for (ProjectBreakWindow window : breaks) {
            jdbc.update(
                    "insert into task_management.project_breaks(project_id, start_time, end_time) values (?, ?, ?)",
                    projectId, window.start(), window.end());
        }
    }
}

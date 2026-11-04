package com.example.taskmanagement.project.persistence;

import com.example.taskmanagement.project.api.MemberBreaks;
import com.example.taskmanagement.project.api.ProjectBreakWindow;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectMemberBreakRepository {
    private final JdbcTemplate jdbc;

    public ProjectMemberBreakRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<Long, List<ProjectBreakWindow>> list(long projectId) {
        List<MemberBreakRow> rows = jdbc.query(
                """
                select user_id, start_time, end_time from task_management.project_member_breaks
                where project_id = ? order by user_id, start_time
                """,
                (rs, rowNum) -> new MemberBreakRow(rs.getLong(1),
                        rs.getObject(2, LocalTime.class),
                        rs.getObject(3, LocalTime.class)),
                projectId);
        Map<Long, List<ProjectBreakWindow>> byUser = new LinkedHashMap<>();
        for (MemberBreakRow row : rows) {
            byUser.computeIfAbsent(row.userId(), ignored -> new ArrayList<>())
                    .add(new ProjectBreakWindow(row.start(), row.end()));
        }
        Map<Long, List<ProjectBreakWindow>> copy = new LinkedHashMap<>();
        for (Map.Entry<Long, List<ProjectBreakWindow>> entry : byUser.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    public void replace(long projectId, List<MemberBreaks> entries) {
        jdbc.update("delete from task_management.project_member_breaks where project_id = ?", projectId);
        for (MemberBreaks entry : entries) {
            for (ProjectBreakWindow window : entry.windows()) {
                jdbc.update(
                        """
                        insert into task_management.project_member_breaks(
                          project_id, user_id, start_time, end_time)
                        values (?, ?, ?, ?)
                        """,
                        projectId, entry.userId(), window.start(), window.end());
            }
        }
    }

    private record MemberBreakRow(long userId, LocalTime start, LocalTime end) {
    }
}

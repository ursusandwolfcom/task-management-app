package com.example.taskmanagement.project.persistence;

import com.example.taskmanagement.project.api.MemberCapacity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectCapacityRepository {
    private final JdbcTemplate jdbc;

    public ProjectCapacityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<Long, Integer> list(long projectId) {
        List<MemberCapacity> rows = jdbc.query(
                """
                select user_id, daily_minutes from task_management.project_member_capacities
                where project_id = ? order by user_id
                """,
                (rs, rowNum) -> new MemberCapacity(rs.getLong(1), rs.getInt(2)),
                projectId);
        Map<Long, Integer> capacities = new HashMap<>();
        for (MemberCapacity row : rows) {
            capacities.put(row.userId(), row.dailyMinutes());
        }
        return Map.copyOf(capacities);
    }

    public void replace(long projectId, Map<Long, Integer> capacities) {
        jdbc.update("delete from task_management.project_member_capacities where project_id = ?", projectId);
        for (Map.Entry<Long, Integer> entry : capacities.entrySet()) {
            jdbc.update(
                    """
                    insert into task_management.project_member_capacities(project_id, user_id, daily_minutes)
                    values (?, ?, ?)
                    """,
                    projectId, entry.getKey(), entry.getValue());
        }
    }
}

package com.example.taskmanagement.task.persistence;

import com.example.taskmanagement.planning.core.DependencyEdge;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** ID-only graph edges; no recursive entity associations or per-node queries. */
@Repository
public class DependencyRepository {
    private final JdbcTemplate jdbc;

    public DependencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DependencyEdge> graphEdges(long projectId, int limit) {
        return jdbc.query("""
                select depends_on_task_id, task_id from task_management.task_dependencies
                where project_id = ? order by task_id, depends_on_task_id limit ?
                """, (row, index) -> new DependencyEdge(row.getLong(1), row.getLong(2)), projectId, limit);
    }
}

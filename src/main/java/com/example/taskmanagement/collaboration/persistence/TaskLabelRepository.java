package com.example.taskmanagement.collaboration.persistence;

import com.example.taskmanagement.collaboration.api.LabelView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Bounded flat queries keep task-label reads independent of lazy entity graphs. */
@Repository
public class TaskLabelRepository {
    private final JdbcTemplate jdbc;

    public TaskLabelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void link(long projectId, long taskId, long labelId) {
        // The project lock serializes the existence check and insertion on both supported databases.
        Integer count = jdbc.queryForObject("select count(*) from task_management.task_labels "
                + "where task_id = ? and label_id = ?", Integer.class, taskId, labelId);
        if (count == 0) {
            jdbc.update("insert into task_management.task_labels(project_id, task_id, label_id) values (?, ?, ?)",
                    projectId, taskId, labelId);
        }
    }

    public void unlink(long taskId, long labelId) {
        jdbc.update("delete from task_management.task_labels where task_id = ? and label_id = ?", taskId, labelId);
    }

    public Page<LabelView> list(long taskId, Pageable paging) {
        var content = jdbc.query("""
                select l.id, l.project_id, l.name, l.color, l.version
                from task_management.labels l join task_management.task_labels tl on tl.label_id = l.id
                where tl.task_id = ? order by l.id desc limit ? offset ?
                """, (row, index) -> new LabelView(row.getLong(1), row.getLong(2), row.getString(3),
                        row.getString(4), row.getLong(5)), taskId, paging.getPageSize(), paging.getOffset());
        long count = jdbc.queryForObject("select count(*) from task_management.task_labels where task_id = ?",
                Long.class, taskId);
        return new PageImpl<>(content, paging, count);
    }
}

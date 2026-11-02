package com.example.taskmanagement.task.persistence;

import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskFilter;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.domain.TaskStatus;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Filtered task pages without lazy graphs; label filter uses task_labels. */
@Repository
public class TaskQueryRepository {
    private static final String SELECT = """
            select t.id, t.project_id, t.name, t.description, t.priority, t.estimated_minutes, t.due_date,
                   t.assignee_id, t.bucket, t.status, t.actual_start, t.actual_end, t.attempt_count, t.version
            from task_management.tasks t
            """;

    private final JdbcTemplate jdbc;

    public TaskQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<TaskView> inProject(long projectId, TaskFilter filter, Pageable paging) {
        var where = new StringBuilder(" where t.project_id = ?");
        var args = new ArrayList<Object>();
        args.add(projectId);
        appendFilters(where, args, filter);
        return page(where.toString(), args, paging);
    }

    /** Assigned tasks visible through membership, or every assigned task for ADMIN. */
    public Page<TaskView> assigned(long userId, boolean admin, TaskFilter filter, Pageable paging) {
        var where = new StringBuilder(" where t.assignee_id = ?");
        var args = new ArrayList<Object>();
        args.add(userId);
        if (!admin) {
            where.append("""
                     and exists (select 1 from task_management.project_members m
                                 where m.project_id = t.project_id and m.user_id = ?)
                    """);
            args.add(userId);
        }
        appendFilters(where, args, new TaskFilter(filter.status(), null, filter.dueFrom(), filter.dueTo(),
                filter.labelId(), filter.bucket()));
        return page(where.toString(), args, paging);
    }

    /**
     * Open assigned tasks in projects the actor can see (or all projects for admin).
     * Overdue = due_date before today and status not completed/cancelled.
     */
    public List<AssigneeLoadRow> workload(long actorId, boolean admin) {
        var sql = new StringBuilder("""
                select t.assignee_id as user_id,
                       count(*) as open_tasks,
                       coalesce(sum(t.estimated_minutes), 0) as estimated_minutes,
                       sum(case when t.due_date is not null and t.due_date < current_date
                                 and t.status not in ('COMPLETED', 'CANCELLED') then 1 else 0 end) as overdue_tasks
                from task_management.tasks t
                where t.assignee_id is not null
                  and t.status in ('NOT_STARTED', 'IN_PROGRESS', 'FAILED')
                """);
        var args = new ArrayList<Object>();
        if (!admin) {
            sql.append("""
                     and exists (select 1 from task_management.project_members m
                                 where m.project_id = t.project_id and m.user_id = ?)
                    """);
            args.add(actorId);
        }
        sql.append(" group by t.assignee_id order by open_tasks desc, user_id asc");
        return jdbc.query(sql.toString(), (row, index) -> new AssigneeLoadRow(
                row.getLong("user_id"),
                row.getLong("open_tasks"),
                row.getLong("estimated_minutes"),
                row.getLong("overdue_tasks")), args.toArray());
    }

    public record AssigneeLoadRow(long userId, long openTasks, long estimatedMinutes, long overdueTasks) {
    }

    private Page<TaskView> page(String where, List<Object> args, Pageable paging) {
        Long total = jdbc.queryForObject("select count(*) from task_management.tasks t" + where, Long.class,
                args.toArray());
        var pageArgs = new ArrayList<>(args);
        pageArgs.add(paging.getPageSize());
        pageArgs.add(paging.getOffset());
        List<TaskView> content = jdbc.query(SELECT + where + " order by t.id desc limit ? offset ?",
                (row, index) -> map(row), pageArgs.toArray());
        return new PageImpl<>(content, paging, total == null ? 0L : total);
    }

    private static void appendFilters(StringBuilder where, List<Object> args, TaskFilter filter) {
        if (filter.status() != null) {
            where.append(" and t.status = ?");
            args.add(filter.status().name());
        }
        if (filter.assigneeId() != null) {
            where.append(" and t.assignee_id = ?");
            args.add(filter.assigneeId());
        }
        if (filter.dueFrom() != null) {
            where.append(" and t.due_date is not null and t.due_date >= ?");
            args.add(Date.valueOf(filter.dueFrom()));
        }
        if (filter.dueTo() != null) {
            where.append(" and t.due_date is not null and t.due_date <= ?");
            args.add(Date.valueOf(filter.dueTo()));
        }
        if (filter.labelId() != null) {
            where.append("""
                     and exists (select 1 from task_management.task_labels tl
                                 where tl.task_id = t.id and tl.label_id = ?)
                    """);
            args.add(filter.labelId());
        }
        if (filter.bucket() != null) {
            where.append(" and t.bucket = ?");
            args.add(filter.bucket().name());
        }
    }

    private static TaskView map(ResultSet row) throws SQLException {
        Date due = row.getDate("due_date");
        Timestamp start = row.getTimestamp("actual_start");
        Timestamp end = row.getTimestamp("actual_end");
        Integer estimate = row.getObject("estimated_minutes") == null ? null : row.getInt("estimated_minutes");
        Long assignee = row.getObject("assignee_id") == null ? null : row.getLong("assignee_id");
        return new TaskView(row.getLong("id"), row.getLong("project_id"), row.getString("name"),
                row.getString("description"), TaskPriority.valueOf(row.getString("priority")), estimate,
                due == null ? null : due.toLocalDate(), assignee, TaskBucket.valueOf(row.getString("bucket")),
                TaskStatus.valueOf(row.getString("status")),
                start == null ? null : start.toInstant(),
                end == null ? null : end.toInstant(),
                row.getInt("attempt_count"), row.getLong("version"));
    }
}

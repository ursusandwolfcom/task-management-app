package com.example.taskmanagement.task.persistence;

import com.example.taskmanagement.shared.persistence.FiniteDateJdbcType;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.domain.TaskExecution;
import com.example.taskmanagement.task.domain.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcType;

@Entity
@Table(name = "tasks", schema = "task_management")
public class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false, updatable = false)
    private long projectId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 10000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority;
    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;
    @JdbcType(FiniteDateJdbcType.class)
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(name = "assignee_id")
    private Long assigneeId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskBucket bucket = TaskBucket.INBOX;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.NOT_STARTED;
    @Column(name = "actual_start")
    private Instant actualStart;
    @Column(name = "actual_end")
    private Instant actualEnd;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Version
    private long version;

    protected TaskEntity() {
    }

    public TaskEntity(long projectId, TaskData data) {
        this.projectId = projectId;
        update(data);
    }

    public TaskView view() {
        return new TaskView(id, projectId, name, description, priority, estimatedMinutes, dueDate,
                assigneeId, bucket, status, actualStart, actualEnd, attemptCount, version);
    }

    public void update(TaskData data) {
        name = data.name();
        description = data.description();
        priority = data.priority();
        estimatedMinutes = data.estimatedMinutes();
        dueDate = data.dueDate();
        assigneeId = data.assigneeId();
        bucket = data.bucket();
    }

    public void execute(TaskExecution execution) {
        status = execution.status();
        attemptCount = execution.attempts().size();
        if (attemptCount > 0) {
            var latest = execution.attempts().getLast();
            actualStart = latest.actualStart();
            actualEnd = latest.actualEnd();
        }
    }
}

package com.example.taskmanagement.task.persistence;

import com.example.taskmanagement.task.api.ActivityView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "task_activities", schema = "task_management")
public class ActivityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false, updatable = false)
    private long projectId;
    @Column(name = "task_id", nullable = false, updatable = false)
    private long taskId;
    @Column(name = "actor_id", nullable = false, updatable = false)
    private long actorId;
    @Column(name = "action_type", nullable = false, length = 100, updatable = false)
    private String actionType;
    @Column(nullable = false, length = 500, updatable = false)
    private String summary;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityEntity() {
    }

    public ActivityEntity(long projectId, long taskId, long actorId, String actionType, String summary,
            Instant createdAt) {
        this.projectId = projectId;
        this.taskId = taskId;
        this.actorId = actorId;
        this.actionType = actionType;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    public ActivityView view() {
        return new ActivityView(id, projectId, taskId, actorId, actionType, summary, createdAt);
    }
}

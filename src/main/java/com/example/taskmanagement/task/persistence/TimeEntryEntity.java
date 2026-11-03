package com.example.taskmanagement.task.persistence;

import com.example.taskmanagement.task.api.TimeEntryView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "task_time_entries", schema = "task_management")
public class TimeEntryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false, updatable = false)
    private long projectId;
    @Column(name = "task_id", nullable = false, updatable = false)
    private long taskId;
    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;
    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;
    @Column(nullable = false, updatable = false)
    private int minutes;
    @Column(length = 500, updatable = false)
    private String note;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TimeEntryEntity() {
    }

    public TimeEntryEntity(long projectId, long taskId, long userId, LocalDate workDate, int minutes, String note,
            Instant createdAt) {
        this.projectId = projectId;
        this.taskId = taskId;
        this.userId = userId;
        this.workDate = workDate;
        this.minutes = minutes;
        this.note = note;
        this.createdAt = createdAt;
    }

    public TimeEntryView view() {
        return new TimeEntryView(id, projectId, taskId, userId, workDate, minutes, note, createdAt);
    }
}

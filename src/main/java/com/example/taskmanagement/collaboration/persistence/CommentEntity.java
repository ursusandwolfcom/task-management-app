package com.example.taskmanagement.collaboration.persistence;

import com.example.taskmanagement.collaboration.api.CommentView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "comments", schema = "task_management")
public class CommentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false, updatable = false)
    private long projectId;
    @Column(name = "task_id", nullable = false, updatable = false)
    private long taskId;
    @Column(name = "author_id", nullable = false, updatable = false)
    private long authorId;
    @Column(nullable = false, length = 10000, updatable = false)
    private String text;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CommentEntity() {
    }

    public CommentEntity(long projectId, long taskId, long authorId, String text, Instant createdAt) {
        this.projectId = projectId;
        this.taskId = taskId;
        this.authorId = authorId;
        this.text = text;
        this.createdAt = createdAt;
    }

    public CommentView view() {
        return new CommentView(id, projectId, taskId, authorId, text, createdAt);
    }
}

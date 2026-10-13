package com.example.taskmanagement.project.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_members", schema = "task_management")
public class ProjectMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private long projectId;
    @Column(name = "user_id", nullable = false)
    private long userId;

    protected ProjectMemberEntity() {
    }

    public ProjectMemberEntity(long projectId, long userId) {
        this.projectId = projectId;
        this.userId = userId;
    }

    public long userId() {
        return userId;
    }
}

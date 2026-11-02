package com.example.taskmanagement.attachment.persistence;

import com.example.taskmanagement.attachment.api.AttachmentView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "attachments", schema = "task_management")
public class AttachmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false, updatable = false)
    private long projectId;
    @Column(name = "task_id", nullable = false, updatable = false)
    private long taskId;
    @Column(name = "uploader_id", nullable = false, updatable = false)
    private long uploaderId;
    @Column(name = "file_name", nullable = false, length = 255, updatable = false)
    private String fileName;
    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;
    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;
    @Column(name = "storage_key", nullable = false, length = 64, updatable = false)
    private String storageKey;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AttachmentEntity() {
    }

    public AttachmentEntity(long projectId, long taskId, long uploaderId, String fileName, String contentType,
            long sizeBytes, String storageKey, Instant createdAt) {
        this.projectId = projectId;
        this.taskId = taskId;
        this.uploaderId = uploaderId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.createdAt = createdAt;
    }

    public String storageKey() {
        return storageKey;
    }

    public AttachmentView view() {
        return new AttachmentView(id, projectId, taskId, uploaderId, fileName, contentType, sizeBytes, createdAt);
    }
}

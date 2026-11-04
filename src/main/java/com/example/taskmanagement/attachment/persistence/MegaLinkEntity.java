package com.example.taskmanagement.attachment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "user_mega_links", schema = "task_management")
public class MegaLinkEntity {
    @Id
    @Column(name = "user_id")
    private long userId;
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;
    @Column(name = "encrypted_session", nullable = false, length = 4096)
    private String encryptedSession;
    @Column(name = "folder_handle", nullable = false, length = 64)
    private String folderHandle;
    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;
    @Version
    private Long version;

    protected MegaLinkEntity() {
    }

    public MegaLinkEntity(long userId, String accountId, String encryptedSession, String folderHandle,
            Instant linkedAt) {
        this.userId = userId;
        update(accountId, encryptedSession, folderHandle, linkedAt);
    }

    public void update(String account, String encrypted, String folder, Instant instant) {
        accountId = account;
        encryptedSession = encrypted;
        folderHandle = folder;
        linkedAt = instant;
    }

    public String accountId() {
        return accountId;
    }

    public String encryptedSession() {
        return encryptedSession;
    }

    public String folderHandle() {
        return folderHandle;
    }

    public Instant linkedAt() {
        return linkedAt;
    }
}

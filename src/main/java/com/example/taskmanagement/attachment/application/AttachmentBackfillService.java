package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.AttachmentBackfillOperations;
import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.persistence.AttachmentRepository;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserRole;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class AttachmentBackfillService implements AttachmentBackfillOperations {
    private final AttachmentRepository attachments;
    private final LocalAttachmentObjectStore local;
    private final AttachmentObjectStore store;
    private final IdentityOperations identity;
    private final String storageMode;

    public AttachmentBackfillService(AttachmentRepository attachments, LocalAttachmentObjectStore local,
            AttachmentObjectStore store, IdentityOperations identity,
            @Value("${attachment.storage:local}") String storageMode) {
        this.attachments = attachments;
        this.local = local;
        this.store = store;
        this.identity = identity;
        this.storageMode = storageMode == null ? "local" : storageMode.strip();
    }

    @Override
    public BackfillResult backfillLocalToDropbox(long actorId, int limit) {
        if (identity.currentRole(actorId) != UserRole.ADMIN) {
            throw new AttachmentException(AttachmentException.Reason.FORBIDDEN);
        }
        if (!"dropbox".equalsIgnoreCase(storageMode)) {
            throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
        }
        int pageSize = Math.min(Math.max(limit, 1), 100);
        var page = attachments.findByExternalIdIsNull(PageRequest.of(0, pageSize));
        int migrated = 0;
        int skipped = 0;
        int failed = 0;
        for (var entity : page.getContent()) {
            try (InputStream body = local.open(entity.storageKey(), null)) {
                String externalId = store.put(entity.storageKey(), body, entity.sizeBytes());
                if (externalId == null || externalId.isBlank()) {
                    skipped++;
                    continue;
                }
                entity.setExternalId(externalId);
                attachments.saveAndFlush(entity);
                migrated++;
            } catch (RuntimeException | java.io.IOException exception) {
                failed++;
            }
        }
        return new BackfillResult(migrated, skipped, failed);
    }
}

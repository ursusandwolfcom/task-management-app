package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.AttachmentOperations;
import com.example.taskmanagement.attachment.api.AttachmentView;
import com.example.taskmanagement.attachment.persistence.AttachmentEntity;
import com.example.taskmanagement.attachment.persistence.AttachmentRepository;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.shared.persistence.PageRequests;
import java.io.InputStream;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class AttachmentService implements AttachmentOperations {
    static final long MAX_BYTES = 5_242_880L;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/webp", "text/plain");

    private final AttachmentRepository attachments;
    private final AttachmentObjectStore store;
    private final AttachmentAccess access;
    private final IdentityOperations identity;
    private final Clock clock;

    public AttachmentService(AttachmentRepository attachments, AttachmentObjectStore store, AttachmentAccess access,
            IdentityOperations identity, Clock clock) {
        this.attachments = attachments;
        this.store = store;
        this.access = access;
        this.identity = identity;
        this.clock = clock;
    }

    @Override
    public AttachmentView upload(long actorId, long taskId, String fileName, String contentType, long sizeBytes,
            InputStream body) {
        String safeName = sanitizeName(fileName);
        String type = normalizeType(contentType);
        if (sizeBytes < 1 || sizeBytes > MAX_BYTES || !ALLOWED_TYPES.contains(type)) {
            throw error(AttachmentException.Reason.VALIDATION_ERROR);
        }
        var project = access.lockTask(actorId, taskId);
        String key = UUID.randomUUID().toString().replace("-", "");
        store.put(key, body, sizeBytes);
        try {
            return attachments.saveAndFlush(new AttachmentEntity(project.id(), taskId, actorId, safeName, type,
                    sizeBytes, key, clock.instant().truncatedTo(ChronoUnit.MICROS))).view();
        } catch (RuntimeException exception) {
            store.delete(key);
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentPage list(long actorId, long taskId, int page, int size) {
        access.task(actorId, taskId);
        var paging = PageRequests.descending(page, size, "id",
                () -> error(AttachmentException.Reason.VALIDATION_ERROR));
        var result = attachments.findByTaskId(taskId, paging);
        return new AttachmentPage(result.getContent().stream().map(AttachmentEntity::view).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentContent download(long actorId, long attachmentId) {
        var entity = attachments.findById(attachmentId)
                .orElseThrow(() -> error(AttachmentException.Reason.ATTACHMENT_NOT_FOUND));
        access.task(actorId, entity.view().taskId());
        return new AttachmentContent(entity.view(), store.open(entity.storageKey()));
    }

    @Override
    public void delete(long actorId, long attachmentId) {
        long projectId = attachments.projectId(attachmentId)
                .orElseThrow(() -> error(AttachmentException.Reason.ATTACHMENT_NOT_FOUND));
        var project = access.lockProject(actorId, projectId, AttachmentException.Reason.ATTACHMENT_NOT_FOUND);
        attachments.projectId(attachmentId)
                .orElseThrow(() -> error(AttachmentException.Reason.ATTACHMENT_NOT_FOUND));
        var entity = attachments.findById(attachmentId)
                .orElseThrow(() -> error(AttachmentException.Reason.ATTACHMENT_NOT_FOUND));
        var view = entity.view();
        if (actorId != view.uploaderId() && actorId != project.ownerId()
                && identity.currentRole(actorId) != UserRole.ADMIN) {
            throw error(AttachmentException.Reason.FORBIDDEN);
        }
        String key = entity.storageKey();
        attachments.delete(entity);
        attachments.flush();
        store.delete(key);
    }

    private static String sanitizeName(String fileName) {
        if (fileName == null) {
            throw error(AttachmentException.Reason.VALIDATION_ERROR);
        }
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.strip();
        if (name.isEmpty() || name.length() > 255 || name.indexOf('\0') >= 0) {
            throw error(AttachmentException.Reason.VALIDATION_ERROR);
        }
        return name;
    }

    private static String normalizeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw error(AttachmentException.Reason.VALIDATION_ERROR);
        }
        String type = contentType.strip().toLowerCase(Locale.ROOT);
        int semicolon = type.indexOf(';');
        return semicolon < 0 ? type : type.substring(0, semicolon).strip();
    }

    private static AttachmentException error(AttachmentException.Reason reason) {
        return new AttachmentException(reason);
    }
}

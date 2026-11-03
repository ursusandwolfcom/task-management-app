package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.task.api.ActivityEvents;
import com.example.taskmanagement.task.api.ActivityOperations;
import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.AttachmentOperations;
import com.example.taskmanagement.attachment.api.AttachmentView;
import com.example.taskmanagement.attachment.persistence.AttachmentEntity;
import com.example.taskmanagement.attachment.persistence.AttachmentRepository;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.integration.outbox.api.OutboxEvents;
import com.example.taskmanagement.integration.outbox.api.OutboxOperations;
import com.example.taskmanagement.shared.persistence.PageRequests;
import java.io.InputStream;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.ObjectMapper;

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
    private final ActivityOperations activity;
    private final OutboxOperations outbox;
    private final ObjectMapper json;
    private final Clock clock;

    public AttachmentService(AttachmentRepository attachments, AttachmentObjectStore store, AttachmentAccess access,
            IdentityOperations identity, ActivityOperations activity, OutboxOperations outbox, ObjectMapper json,
            Clock clock) {
        this.attachments = attachments;
        this.store = store;
        this.access = access;
        this.identity = identity;
        this.activity = activity;
        this.outbox = outbox;
        this.json = json;
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
        var task = access.task(actorId, taskId);
        String key = UUID.randomUUID().toString().replace("-", "");
        String externalId = store.put(key, body, sizeBytes);
        try {
            var created = attachments.saveAndFlush(new AttachmentEntity(project.id(), taskId, actorId, safeName, type,
                    sizeBytes, key, externalId, clock.instant().truncatedTo(ChronoUnit.MICROS))).view();
            activity.record(actorId, project.id(), taskId, ActivityEvents.ATTACHMENT_UPLOADED,
                    "Файл: " + created.fileName());
            var bodyJson = new LinkedHashMap<String, Object>();
            bodyJson.put("actorId", actorId);
            bodyJson.put("assigneeId", task.assigneeId());
            bodyJson.put("projectId", project.id());
            bodyJson.put("taskId", taskId);
            bodyJson.put("fileName", created.fileName());
            outbox.append(OutboxEvents.ATTACHMENT_UPLOADED, json.writeValueAsString(bodyJson));
            return created;
        } catch (RuntimeException exception) {
            store.delete(key, externalId);
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
        return new AttachmentContent(entity.view(), store.open(entity.storageKey(), entity.externalId()));
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
        String externalId = entity.externalId();
        activity.record(actorId, projectId, view.taskId(), ActivityEvents.ATTACHMENT_DELETED,
                "Удалён файл: " + view.fileName());
        attachments.delete(entity);
        attachments.flush();
        store.delete(key, externalId);
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

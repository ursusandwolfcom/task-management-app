package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.AttachmentResponse;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Attachment;
import com.example.taskmanagement.repository.AttachmentRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AttachmentService {
    public record Download(String filename, byte[] content) {
    }

    private final AttachmentRepository attachments;
    private final TaskService tasks;
    private final DropboxService dropbox;
    private final ResponseMapper mapper;
    private final Clock clock;

    public AttachmentService(AttachmentRepository attachments, TaskService tasks, DropboxService dropbox,
            ResponseMapper mapper, Clock clock) {
        this.attachments = attachments;
        this.tasks = tasks;
        this.dropbox = dropbox;
        this.mapper = mapper;
        this.clock = clock;
    }

    public AttachmentResponse upload(Long actorId, Long taskId, MultipartFile file) throws IOException {
        var task = tasks.writable(actorId, taskId);
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.length() > AttachmentLimits.MAX_FILENAME_LENGTH
                || filename.equals(".") || filename.equals("..")
                || filename.chars().anyMatch(value -> Character.isISOControl(value) || "\\/:".indexOf(value) >= 0)
                || file.isEmpty() || file.getSize() > AttachmentLimits.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename or file size (1 B to 5 MB)");
        }
        String externalId;
        try (var input = file.getInputStream()) {
            externalId = dropbox.upload(filename, input);
        }
        var attachment = new Attachment();
        attachment.setTask(task);
        attachment.setDropboxFileId(externalId);
        attachment.setFilename(filename);
        attachment.setUploadDate(LocalDateTime.now(clock));
        try {
            return mapper.toResponse(attachments.saveAndFlush(attachment));
        } catch (RuntimeException failure) {
            try {
                dropbox.delete(externalId);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    public Page<AttachmentResponse> list(Long actorId, Long taskId, Pageable pageable) {
        tasks.accessible(actorId, taskId);
        return attachments.findByTaskId(taskId, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Download download(Long actorId, Long id) throws IOException {
        Attachment attachment = accessible(actorId, id);
        return new Download(attachment.getFilename(), dropbox.download(attachment.getDropboxFileId()));
    }

    public void delete(Long actorId, Long id) {
        Attachment attachment = found(id);
        tasks.writable(actorId, attachment.getTask().getId());
        dropbox.delete(attachment.getDropboxFileId());
        attachments.delete(attachment);
    }

    private Attachment accessible(Long actorId, Long id) {
        Attachment attachment = found(id);
        tasks.accessible(actorId, attachment.getTask().getId());
        return attachment;
    }

    private Attachment found(Long id) {
        return attachments.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
    }
}

package com.example.taskmanagement.attachment.api;

import java.io.InputStream;
import java.util.List;

public interface AttachmentOperations {
    record AttachmentPage(List<AttachmentView> content, int page, int size, long totalElements, int totalPages) {
        public AttachmentPage {
            content = List.copyOf(content);
        }
    }

    record AttachmentContent(AttachmentView metadata, InputStream body) {
    }

    AttachmentView upload(long actorId, long taskId, String fileName, String contentType, long sizeBytes,
            InputStream body);

    AttachmentPage list(long actorId, long taskId, int page, int size);

    AttachmentContent download(long actorId, long attachmentId);

    void delete(long actorId, long attachmentId);
}

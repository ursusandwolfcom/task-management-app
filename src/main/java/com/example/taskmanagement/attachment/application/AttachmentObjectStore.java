package com.example.taskmanagement.attachment.application;

import java.io.InputStream;

/**
 * Strategy for attachment bytes. Metadata stays in PostgreSQL.
 * Implementations: local disk (default) or Dropbox when {@code attachment.storage=dropbox}.
 */
public interface AttachmentObjectStore {
    /**
     * Stores bytes under the opaque key. Returns a provider-specific external id (Dropbox file id),
     * or {@code null} for local disk.
     */
    String put(String key, InputStream body, long sizeBytes);

    InputStream open(String key, String externalId);

    void delete(String key, String externalId);
}

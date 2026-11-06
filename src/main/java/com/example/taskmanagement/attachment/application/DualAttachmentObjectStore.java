package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.AttachmentException;
import java.io.InputStream;

/**
 * Dropbox primary with local fallback for legacy rows (open/delete) during backfill.
 */
public class DualAttachmentObjectStore implements AttachmentObjectStore {
    private final AttachmentObjectStore dropbox;
    private final AttachmentObjectStore local;

    public DualAttachmentObjectStore(AttachmentObjectStore dropbox, AttachmentObjectStore local) {
        this.dropbox = dropbox;
        this.local = local;
    }

    @Override
    public String put(String key, InputStream body, long sizeBytes) {
        return dropbox.put(key, body, sizeBytes);
    }

    @Override
    public InputStream open(String key, String externalId) {
        if (externalId != null && !externalId.isBlank()) {
            try {
                return dropbox.open(key, externalId);
            } catch (AttachmentException ignored) {
                return local.open(key, null);
            }
        }
        try {
            return local.open(key, null);
        } catch (AttachmentException localMiss) {
            return dropbox.open(key, null);
        }
    }

    @Override
    public void delete(String key, String externalId) {
        if (externalId == null || externalId.isBlank()) {
            local.delete(key, null);
            return;
        }
        dropbox.delete(key, externalId);
        try {
            local.delete(key, null);
        } catch (RuntimeException ignored) {
            // A leftover legacy copy is secondary to the confirmed remote deletion.
        }
    }
}

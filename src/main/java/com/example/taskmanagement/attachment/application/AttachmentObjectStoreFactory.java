package com.example.taskmanagement.attachment.application;

/** Resolves the object store for a user (per-user Dropbox when linked, else app default). */
public interface AttachmentObjectStoreFactory {
    AttachmentObjectStore forUser(long userId);

    /** Read/delete use the original reference, never the current uploader's destination preference. */
    default AttachmentObjectStore forAttachment(long userId, String externalId) {
        return forUser(userId);
    }
}

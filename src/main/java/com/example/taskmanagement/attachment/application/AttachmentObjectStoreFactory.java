package com.example.taskmanagement.attachment.application;

/** Resolves the object store for a user (per-user Dropbox when linked, else app default). */
public interface AttachmentObjectStoreFactory {
    AttachmentObjectStore forUser(long userId);
}

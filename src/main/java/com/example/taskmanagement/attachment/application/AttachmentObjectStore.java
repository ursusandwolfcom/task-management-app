package com.example.taskmanagement.attachment.application;

import java.io.InputStream;

/** Binary object store; metadata lives in PostgreSQL. Dropbox can replace this later. */
public interface AttachmentObjectStore {
    void put(String key, InputStream body, long sizeBytes);

    InputStream open(String key);

    void delete(String key);
}

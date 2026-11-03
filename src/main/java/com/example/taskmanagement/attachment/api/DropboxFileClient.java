package com.example.taskmanagement.attachment.api;

import java.io.InputStream;

/** Narrow Dropbox Files API surface for the attachment Strategy (testable). */
public interface DropboxFileClient {
    String upload(String path, InputStream body, long sizeBytes);

    InputStream download(String path, String fileId);

    void delete(String path, String fileId);
}

package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.DropboxFileClient;
import java.io.InputStream;

public class DropboxAttachmentObjectStore implements AttachmentObjectStore {
    private final DropboxFileClient client;
    private final String folder;

    public DropboxAttachmentObjectStore(DropboxFileClient client, String appFolder) {
        this.client = client;
        String trimmed = appFolder == null ? "" : appFolder.strip();
        if (trimmed.isEmpty()) {
            trimmed = "/projectflow";
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        this.folder = trimmed;
    }

    @Override
    public String put(String key, InputStream body, long sizeBytes) {
        assertKey(key);
        try {
            return client.upload(pathFor(key), body, sizeBytes);
        } catch (AttachmentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
    }

    @Override
    public InputStream open(String key, String externalId) {
        assertKey(key);
        try {
            return client.download(pathFor(key), externalId);
        } catch (AttachmentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
    }

    @Override
    public void delete(String key, String externalId) {
        assertKey(key);
        try {
            client.delete(pathFor(key), externalId);
        } catch (AttachmentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
    }

    private String pathFor(String key) {
        return folder + "/" + key;
    }

    private static void assertKey(String key) {
        if (key == null || key.isBlank() || key.contains("/") || key.contains("\\") || key.contains("..")) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
    }
}

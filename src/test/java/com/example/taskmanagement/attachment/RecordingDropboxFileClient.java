package com.example.taskmanagement.attachment;

import com.example.taskmanagement.attachment.api.DropboxFileClient;
import com.example.taskmanagement.attachment.api.AttachmentException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class RecordingDropboxFileClient implements DropboxFileClient {
    private final Map<String, byte[]> byPath = new ConcurrentHashMap<>();
    private final Map<String, String> idByPath = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();
    boolean failDelete;
    final AtomicInteger deleteCalls = new AtomicInteger();

    @Override
    public String upload(String path, InputStream body, long sizeBytes) {
        try {
            byte[] bytes = body.readAllBytes();
            if (bytes.length != sizeBytes) {
                throw new IllegalStateException("size mismatch");
            }
            String id = "id:rec-" + seq.incrementAndGet();
            byPath.put(path, bytes);
            idByPath.put(path, id);
            return id;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public InputStream download(String path, String fileId) {
        byte[] bytes = byPath.get(path);
        if (bytes == null && fileId != null) {
            bytes = idByPath.entrySet().stream()
                    .filter(e -> e.getValue().equals(fileId))
                    .map(e -> byPath.get(e.getKey()))
                    .findFirst()
                    .orElse(null);
        }
        if (bytes == null) {
            throw new IllegalStateException("missing " + path);
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void delete(String path, String fileId) {
        deleteCalls.incrementAndGet();
        if (failDelete) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
        byPath.remove(path);
        idByPath.remove(path);
    }

    int size() {
        return byPath.size();
    }

    void clear() {
        byPath.clear();
        idByPath.clear();
        failDelete = false;
        deleteCalls.set(0);
    }
}

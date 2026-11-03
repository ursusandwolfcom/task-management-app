package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.AttachmentException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalAttachmentObjectStore implements AttachmentObjectStore {
    private final Path root;

    public LocalAttachmentObjectStore(String storageDir) throws IOException {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String put(String key, InputStream body, long sizeBytes) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(target)) {
                long copied = body.transferTo(out);
                if (copied != sizeBytes) {
                    Files.deleteIfExists(target);
                    throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
                }
            }
            return null;
        } catch (IOException exception) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
    }

    @Override
    public InputStream open(String key, String externalId) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException exception) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
    }

    @Override
    public void delete(String key, String externalId) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
    }

    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root) || key.contains("/") || key.contains("\\") || key.contains("..")) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
        return resolved;
    }
}

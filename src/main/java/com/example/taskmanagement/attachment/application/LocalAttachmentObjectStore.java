package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.AttachmentException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class LocalAttachmentObjectStore implements AttachmentObjectStore {
    private static final long MAX_BYTES = 5_242_880L;
    private final Path root;

    public LocalAttachmentObjectStore(String storageDir) throws IOException {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String put(String key, InputStream body, long sizeBytes) {
        if (sizeBytes < 1 || sizeBytes > MAX_BYTES) {
            throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
        }
        Path target = resolve(key);
        boolean created = false;
        boolean completed = false;
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(target,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                copy(body, out, sizeBytes);
            }
            completed = true;
            return null;
        } catch (IOException exception) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        } finally {
            if (created && !completed) {
                cleanup(target);
            }
        }
    }

    private static void copy(InputStream body, OutputStream output, long sizeBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long copied = 0;
        int count;
        while ((count = body.read(buffer, 0, (int) Math.min(buffer.length, sizeBytes - copied + 1))) != -1) {
            copied += count;
            if (copied > sizeBytes) {
                throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
            }
            output.write(buffer, 0, count);
        }
        if (copied != sizeBytes) {
            throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
        }
    }

    private static void cleanup(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            target.toFile().deleteOnExit();
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

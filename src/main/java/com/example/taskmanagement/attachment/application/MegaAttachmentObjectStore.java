package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.MegaClient;
import com.example.taskmanagement.attachment.api.MegaException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** References bind each object to its original account, independent of later link changes. */
public class MegaAttachmentObjectStore implements AttachmentObjectStore {
    private static final int MAX_BYTES = 5_242_880;
    private final MegaClient client;
    private final MegaClient.Session session;

    public MegaAttachmentObjectStore(MegaClient client, MegaClient.Session session) {
        this.client = client;
        this.session = session;
    }

    @Override
    public String put(String key, InputStream body, long sizeBytes) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,64}") || sizeBytes < 1 || sizeBytes > MAX_BYTES) {
            throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
        }
        Path temporary = null;
        try {
            byte[] bytes = body.readNBytes(MAX_BYTES + 1);
            if (bytes.length != sizeBytes) {
                throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
            }
            temporary = Files.createTempFile("projectflow-mega-upload-", ".bin");
            Files.write(temporary, bytes);
            String handle = client.upload(session, temporary, key);
            if (!MegaAccountService.validHandle(handle)) {
                throw new MegaException(MegaException.Reason.MEGA_PROTOCOL_ERROR);
            }
            return "mega:" + session.accountId() + ":" + handle;
        } catch (IOException exception) {
            throw new MegaException(MegaException.Reason.MEGA_STORAGE_ERROR);
        } finally {
            cleanup(temporary);
        }
    }

    @Override
    public InputStream open(String key, String externalId) {
        String handle = handle(externalId);
        Path temporary = null;
        try {
            temporary = Files.createTempFile("projectflow-mega-download-", ".bin");
            client.download(session, handle, temporary);
            if (Files.size(temporary) > MAX_BYTES) {
                throw new MegaException(MegaException.Reason.MEGA_STORAGE_ERROR);
            }
            return new ByteArrayInputStream(Files.readAllBytes(temporary));
        } catch (IOException exception) {
            throw new MegaException(MegaException.Reason.MEGA_STORAGE_ERROR);
        } finally {
            cleanup(temporary);
        }
    }

    @Override
    public void delete(String key, String externalId) {
        client.remove(session, handle(externalId));
    }

    private String handle(String externalId) {
        String[] parts = externalId == null ? new String[0] : externalId.split(":", -1);
        if (parts.length != 3 || !parts[0].equals("mega") || !parts[1].equals(session.accountId())
                || !MegaAccountService.validHandle(parts[2])) {
            throw new MegaException(MegaException.Reason.MEGA_ACCOUNT_UNAVAILABLE);
        }
        return parts[2];
    }

    private static void cleanup(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                path.toFile().deleteOnExit();
            }
        }
    }
}

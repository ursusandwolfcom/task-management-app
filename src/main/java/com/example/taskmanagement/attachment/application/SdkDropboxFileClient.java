package com.example.taskmanagement.attachment.application;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.WriteMode;
import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.DropboxFileClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class SdkDropboxFileClient implements DropboxFileClient {
    private final DbxClientV2 client;

    public SdkDropboxFileClient(String accessToken) {
        this(accessToken, null, null, null);
    }

    public SdkDropboxFileClient(String accessToken, String refreshToken, String appKey, String appSecret) {
        DbxRequestConfig config = new DbxRequestConfig("ProjectFlow");
        String access = blank(accessToken);
        String refresh = blank(refreshToken);
        String key = blank(appKey);
        String secret = blank(appSecret);
        if (!refresh.isEmpty() && !key.isEmpty() && !secret.isEmpty()) {
            DbxCredential credential = new DbxCredential(access.isEmpty() ? null : access, -1L, refresh, key, secret);
            this.client = new DbxClientV2(config, credential);
        } else if (!access.isEmpty()) {
            this.client = new DbxClientV2(config, access);
        } else {
            throw new IllegalStateException(
                    "dropbox requires DROPBOX_ACCESS_TOKEN or DROPBOX_REFRESH_TOKEN+APP_KEY+APP_SECRET");
        }
    }

    @Override
    public String upload(String path, InputStream body, long sizeBytes) {
        try {
            FileMetadata meta = client.files().uploadBuilder(path)
                    .withMode(WriteMode.OVERWRITE)
                    .withMute(true)
                    .uploadAndFinish(body, sizeBytes);
            return meta.getId();
        } catch (DbxException | java.io.IOException exception) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
    }

    @Override
    public InputStream download(String path, String fileId) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            String target = fileId != null && !fileId.isBlank() ? fileId : path;
            client.files().downloadBuilder(target).download(buffer);
            return new ByteArrayInputStream(buffer.toByteArray());
        } catch (DbxException | java.io.IOException exception) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
    }

    @Override
    public void delete(String path, String fileId) {
        try {
            String target = fileId != null && !fileId.isBlank() ? fileId : path;
            client.files().deleteV2(target);
        } catch (DbxException exception) {
            // best-effort delete per DROPBOX_DECISIONS
        }
    }

    private static String blank(String value) {
        return value == null ? "" : value.strip();
    }
}

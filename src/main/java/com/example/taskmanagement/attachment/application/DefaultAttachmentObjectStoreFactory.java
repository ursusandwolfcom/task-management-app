package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.identity.api.DropboxAccountOperations;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;

public class DefaultAttachmentObjectStoreFactory implements AttachmentObjectStoreFactory {
    private final AttachmentObjectStore fallback;
    private final DropboxAccountOperations accounts;
    private final String appKey;
    private final String appSecret;
    private final String appFolder;
    private final boolean dropboxEnabled;
    private final Map<String, AttachmentObjectStore> cache = new ConcurrentHashMap<>();

    public DefaultAttachmentObjectStoreFactory(AttachmentObjectStore fallback, DropboxAccountOperations accounts,
            @Value("${attachment.storage:local}") String storageMode,
            @Value("${dropbox.app-key:}") String appKey, @Value("${dropbox.app-secret:}") String appSecret,
            @Value("${dropbox.app-folder:/projectflow}") String appFolder) {
        this.fallback = fallback;
        this.accounts = accounts;
        this.appKey = blank(appKey);
        this.appSecret = blank(appSecret);
        this.appFolder = appFolder;
        this.dropboxEnabled = "dropbox".equalsIgnoreCase(blank(storageMode));
    }

    @Override
    public AttachmentObjectStore forUser(long userId) {
        if (!dropboxEnabled || appKey.isEmpty() || appSecret.isEmpty()) {
            return fallback;
        }
        return accounts.credential(userId)
                .map(credential -> cache.computeIfAbsent(credential.refreshToken(),
                        token -> new DropboxAttachmentObjectStore(
                                new SdkDropboxFileClient("", token, appKey, appSecret),
                                appFolder + "/u" + userId)))
                .orElse(fallback);
    }

    private static String blank(String value) {
        return value == null ? "" : value.strip();
    }
}

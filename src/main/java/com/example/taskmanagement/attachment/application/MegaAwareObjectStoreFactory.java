package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.MegaClient;
import com.example.taskmanagement.attachment.api.MegaException;

public class MegaAwareObjectStoreFactory implements AttachmentObjectStoreFactory {
    private final AttachmentObjectStoreFactory legacy;
    private final MegaAccountService accounts;
    private final MegaClient client;

    public MegaAwareObjectStoreFactory(AttachmentObjectStoreFactory legacy, MegaAccountService accounts,
            MegaClient client) {
        this.legacy = legacy;
        this.accounts = accounts;
        this.client = client;
    }

    @Override
    public AttachmentObjectStore forUser(long userId) {
        if (!accounts.status(userId).linked()) {
            return legacy.forUser(userId);
        }
        return mega(userId);
    }

    @Override
    public AttachmentObjectStore forAttachment(long userId, String externalId) {
        return externalId != null && externalId.startsWith("mega:")
                ? mega(userId) : legacy.forAttachment(userId, externalId);
    }

    private AttachmentObjectStore mega(long userId) {
        if (!accounts.configured()) {
            throw new MegaException(MegaException.Reason.MEGA_NOT_CONFIGURED);
        }
        var credential = accounts.credential(userId)
                .orElseThrow(() -> new MegaException(MegaException.Reason.MEGA_ACCOUNT_UNAVAILABLE));
        return new MegaAttachmentObjectStore(client, credential);
    }
}

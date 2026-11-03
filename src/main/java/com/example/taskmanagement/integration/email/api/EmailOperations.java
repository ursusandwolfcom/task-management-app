package com.example.taskmanagement.integration.email.api;

import com.example.taskmanagement.identity.api.EmailDigestMode;

/** Optional SMTP channel on top of in-app notifications. */
public interface EmailOperations {
    record EmailChannelStatus(boolean configured, boolean enabled, String address, EmailDigestMode digestMode,
            long pendingDigestLines) {
    }

    EmailChannelStatus status(long userId);

    void setEnabled(long userId, boolean enabled);

    void setDigestMode(long userId, EmailDigestMode mode);

    int deliverPending(int limit);

    /** Send queued DAILY digests (scheduler + tests). */
    int flushDigests();
}

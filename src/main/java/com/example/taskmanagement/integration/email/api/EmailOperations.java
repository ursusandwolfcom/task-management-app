package com.example.taskmanagement.integration.email.api;

/** Optional SMTP channel on top of in-app notifications. */
public interface EmailOperations {
    record EmailChannelStatus(boolean configured, boolean enabled, String address) {
    }

    EmailChannelStatus status(long userId);

    void setEnabled(long userId, boolean enabled);

    int deliverPending(int limit);
}

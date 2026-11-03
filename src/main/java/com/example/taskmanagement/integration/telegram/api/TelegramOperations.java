package com.example.taskmanagement.integration.telegram.api;

import java.time.Instant;

/** Optional Telegram channel on top of in-app notifications. */
public interface TelegramOperations {
    record TelegramChannelStatus(boolean configured, boolean linked, boolean enabled, String botUsername,
            String deepLinkPrefix) {
    }

    record LinkResponse(String token, String deepLink, Instant expiresAt) {
    }

    TelegramChannelStatus status(long userId);

    LinkResponse createLink(long userId);

    void setEnabled(long userId, boolean enabled);

    void unlink(long userId);

    /** Applies a Bot API update; returns true when a /start link was accepted. */
    boolean handleUpdate(String secretHeader, String rawBody);

    int deliverPending(int limit);
}

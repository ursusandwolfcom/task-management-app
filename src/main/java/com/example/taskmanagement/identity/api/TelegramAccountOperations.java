package com.example.taskmanagement.identity.api;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Telegram chat binding lives on users; delivery stays in integration.telegram. */
public interface TelegramAccountOperations {
    record TelegramStatus(boolean linked, boolean enabled) {
    }

    record LinkTicket(String token, Instant expiresAt) {
    }

    record TelegramTarget(long userId, long chatId) {
    }

    TelegramStatus status(long userId);

    LinkTicket createLinkTicket(long userId);

    void bindChat(String token, long chatId);

    void unlink(long userId);

    void setEnabled(long userId, boolean enabled);

    Optional<TelegramTarget> enabledTarget(long userId);

    Map<Long, Long> enabledChatIds(Collection<Long> userIds);
}

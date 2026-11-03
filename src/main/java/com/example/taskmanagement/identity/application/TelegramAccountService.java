package com.example.taskmanagement.identity.application;

import com.example.taskmanagement.identity.api.IdentityException;
import com.example.taskmanagement.identity.api.TelegramAccountOperations;
import com.example.taskmanagement.identity.persistence.TelegramLinkTokenEntity;
import com.example.taskmanagement.identity.persistence.TelegramLinkTokenRepository;
import com.example.taskmanagement.identity.persistence.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class TelegramAccountService implements TelegramAccountOperations {
    private static final Duration LINK_TTL = Duration.ofMinutes(15);
    private final UserRepository users;
    private final TelegramLinkTokenRepository tokens;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TelegramAccountService(UserRepository users, TelegramLinkTokenRepository tokens, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TelegramStatus status(long userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new IdentityException(IdentityException.Reason.USER_NOT_FOUND));
        return new TelegramStatus(user.telegramChatId() != null, user.telegramEnabled());
    }

    @Override
    public LinkTicket createLinkTicket(long userId) {
        if (users.findById(userId).isEmpty()) {
            throw new IdentityException(IdentityException.Reason.USER_NOT_FOUND);
        }
        String token = newToken();
        var expires = clock.instant().truncatedTo(ChronoUnit.MICROS).plus(LINK_TTL);
        tokens.saveAndFlush(new TelegramLinkTokenEntity(token, userId, expires));
        return new LinkTicket(token, expires);
    }

    @Override
    public void bindChat(String token, long chatId) {
        if (token == null || token.isBlank() || chatId == 0L) {
            throw new IdentityException(IdentityException.Reason.TELEGRAM_LINK_INVALID);
        }
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var ticket = tokens.findById(token.strip())
                .orElseThrow(() -> new IdentityException(IdentityException.Reason.TELEGRAM_LINK_INVALID));
        if (ticket.usedAt() != null || ticket.expiresAt().isBefore(now)) {
            throw new IdentityException(IdentityException.Reason.TELEGRAM_LINK_INVALID);
        }
        var occupant = users.findByTelegramChatId(chatId);
        if (occupant.isPresent() && occupant.orElseThrow().view().id() != ticket.userId()) {
            throw new IdentityException(IdentityException.Reason.TELEGRAM_CHAT_TAKEN);
        }
        var user = users.lockById(ticket.userId())
                .orElseThrow(() -> new IdentityException(IdentityException.Reason.USER_NOT_FOUND));
        user.bindTelegramChat(chatId);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new IdentityException(IdentityException.Reason.TELEGRAM_CHAT_TAKEN);
        }
        ticket.markUsed(now);
        tokens.saveAndFlush(ticket);
    }

    @Override
    public void unlink(long userId) {
        var user = users.lockById(userId)
                .orElseThrow(() -> new IdentityException(IdentityException.Reason.USER_NOT_FOUND));
        user.unlinkTelegram();
        users.saveAndFlush(user);
    }

    @Override
    public void setEnabled(long userId, boolean enabled) {
        var user = users.lockById(userId)
                .orElseThrow(() -> new IdentityException(IdentityException.Reason.USER_NOT_FOUND));
        user.setTelegramEnabled(enabled);
        users.saveAndFlush(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TelegramTarget> enabledTarget(long userId) {
        return users.findById(userId)
                .filter(user -> user.telegramChatId() != null && user.telegramEnabled())
                .map(user -> new TelegramTarget(user.view().id(), user.telegramChatId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> enabledChatIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> chats = new HashMap<>();
        for (var user : users.findEnabledTelegramTargets(userIds)) {
            chats.put(user.view().id(), user.telegramChatId());
        }
        return chats;
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

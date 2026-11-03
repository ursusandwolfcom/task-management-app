package com.example.taskmanagement.integration.telegram.application;

import com.example.taskmanagement.identity.api.TelegramAccountOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxConsumerOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxEvents;
import com.example.taskmanagement.integration.outbox.api.OutboxMessage;
import com.example.taskmanagement.integration.telegram.api.TelegramBotClient;
import com.example.taskmanagement.integration.telegram.api.TelegramException;
import com.example.taskmanagement.integration.telegram.api.TelegramOperations;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@Transactional
public class TelegramService implements TelegramOperations {
    private final TelegramAccountOperations accounts;
    private final OutboxConsumerOperations outbox;
    private final TelegramBotClient bot;
    private final ObjectMapper json;
    private final String botToken;
    private final String botUsername;
    private final String webhookSecret;
    private final String appPublicBase;

    public TelegramService(TelegramAccountOperations accounts, OutboxConsumerOperations outbox, TelegramBotClient bot,
            ObjectMapper json, @Value("${telegram.bot-token:}") String botToken,
            @Value("${telegram.bot-username:}") String botUsername,
            @Value("${telegram.webhook-secret:}") String webhookSecret,
            @Value("${telegram.app-public-base:}") String appPublicBase) {
        this.accounts = accounts;
        this.outbox = outbox;
        this.bot = bot;
        this.json = json;
        this.botToken = blankToEmpty(botToken);
        this.botUsername = blankToEmpty(botUsername);
        this.webhookSecret = blankToEmpty(webhookSecret);
        this.appPublicBase = trimTrailingSlash(blankToEmpty(appPublicBase));
    }

    @Override
    @Transactional(readOnly = true)
    public TelegramChannelStatus status(long userId) {
        var account = accounts.status(userId);
        return new TelegramChannelStatus(configured(), account.linked(), account.enabled(), botUsername,
                deepLinkPrefix());
    }

    @Override
    public LinkResponse createLink(long userId) {
        requireConfigured();
        if (botUsername.isEmpty()) {
            throw new TelegramException(TelegramException.Reason.DISABLED);
        }
        var ticket = accounts.createLinkTicket(userId);
        return new LinkResponse(ticket.token(), deepLinkPrefix() + ticket.token(), ticket.expiresAt());
    }

    @Override
    public void setEnabled(long userId, boolean enabled) {
        accounts.setEnabled(userId, enabled);
    }

    @Override
    public void unlink(long userId) {
        accounts.unlink(userId);
    }

    @Override
    public boolean handleUpdate(String secretHeader, String rawBody) {
        requireConfigured();
        if (!webhookSecret.isEmpty() && !constantTimeEquals(webhookSecret, secretHeader)) {
            throw new TelegramException(TelegramException.Reason.FORBIDDEN);
        }
        JsonNode root = parse(rawBody == null ? "" : rawBody);
        JsonNode message = root.path("message");
        String text = message.path("text").asString("");
        long chatId = message.path("chat").path("id").asLong(0);
        if (chatId == 0L || text.isBlank() || !text.startsWith("/start")) {
            return false;
        }
        String payload = text.length() > 6 ? text.substring(6).strip() : "";
        if (payload.isEmpty()) {
            return false;
        }
        accounts.bindChat(payload, chatId);
        bot.sendMessage(chatId, "ProjectFlow: Telegram привязан. Уведомления включены.");
        return true;
    }

    @Override
    public int deliverPending(int limit) {
        if (!configured()) {
            return 0;
        }
        int delivered = 0;
        for (OutboxMessage message : outbox.claimTelegramUnprocessed(limit)) {
            deliver(message);
            outbox.markTelegramProcessed(message.id());
            delivered++;
        }
        return delivered;
    }

    private void deliver(OutboxMessage message) {
        if (!isTelegramEvent(message.eventType())) {
            return;
        }
        JsonNode payload = parse(message.payload());
        long actor = actorId(message.eventType(), payload);
        Set<Long> recipients = recipients(message.eventType(), payload);
        recipients.remove(actor);
        if (recipients.isEmpty()) {
            return;
        }
        var chats = accounts.enabledChatIds(recipients);
        String text = format(message.eventType(), payload);
        for (Long userId : recipients) {
            Long chatId = chats.get(userId);
            if (chatId != null) {
                bot.sendMessage(chatId, text);
            }
        }
    }

    private static boolean isTelegramEvent(String eventType) {
        return OutboxEvents.TASK_ASSIGNED.equals(eventType)
                || OutboxEvents.COMMENT_CREATED.equals(eventType)
                || OutboxEvents.TASK_COMPLETED.equals(eventType)
                || OutboxEvents.TASK_FAILED.equals(eventType);
    }

    private static long actorId(String eventType, JsonNode payload) {
        if (OutboxEvents.COMMENT_CREATED.equals(eventType)) {
            return payload.path("authorId").asLong(0);
        }
        return payload.path("actorId").asLong(0);
    }

    private static Set<Long> recipients(String eventType, JsonNode payload) {
        Set<Long> recipients = new LinkedHashSet<>();
        long assignee = payload.path("assigneeId").asLong(0);
        if (assignee > 0) {
            recipients.add(assignee);
        }
        if (OutboxEvents.TASK_COMPLETED.equals(eventType) || OutboxEvents.TASK_FAILED.equals(eventType)) {
            long owner = payload.path("ownerId").asLong(0);
            if (owner > 0) {
                recipients.add(owner);
            }
        }
        return recipients;
    }

    private String format(String eventType, JsonNode payload) {
        String title = switch (eventType) {
            case OutboxEvents.TASK_ASSIGNED -> "Назначена задача";
            case OutboxEvents.COMMENT_CREATED -> "Новый комментарий";
            case OutboxEvents.TASK_COMPLETED -> "Задача завершена";
            case OutboxEvents.TASK_FAILED -> "Задача провалена";
            default -> "Обновление";
        };
        String body = truncate(switch (eventType) {
            case OutboxEvents.COMMENT_CREATED -> payload.path("preview").asString("Комментарий");
            default -> payload.path("taskName").asString("Задача");
        });
        long taskId = payload.path("taskId").asLong(0);
        String link = taskId > 0 ? taskLink(taskId) : "";
        return link.isEmpty() ? title + "\n" + body : title + "\n" + body + "\n" + link;
    }

    private String taskLink(long taskId) {
        if (appPublicBase.isEmpty()) {
            return "#/tasks/" + taskId;
        }
        return appPublicBase + "/#/tasks/" + taskId;
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new TelegramException(TelegramException.Reason.DISABLED);
        }
    }

    private boolean configured() {
        return !botToken.isEmpty();
    }

    private String deepLinkPrefix() {
        return botUsername.isEmpty() ? "" : "https://t.me/" + botUsername + "?start=";
    }

    private JsonNode parse(String payload) {
        try {
            return json.readTree(payload);
        } catch (RuntimeException exception) {
            throw new TelegramException(TelegramException.Reason.VALIDATION_ERROR);
        }
    }

    private static String truncate(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return "—";
        }
        return text.length() <= 200 ? text : text.substring(0, 200);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}

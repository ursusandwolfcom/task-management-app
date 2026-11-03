package com.example.taskmanagement.integration.email.application;

import com.example.taskmanagement.identity.api.EmailPreferenceOperations;
import com.example.taskmanagement.integration.email.api.EmailException;
import com.example.taskmanagement.integration.email.api.EmailOperations;
import com.example.taskmanagement.integration.email.api.EmailSender;
import com.example.taskmanagement.integration.outbox.api.OutboxConsumerOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxMessage;
import com.example.taskmanagement.integration.outbox.api.OutboxPushProjection;
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
public class EmailService implements EmailOperations {
    private final EmailPreferenceOperations preferences;
    private final OutboxConsumerOperations outbox;
    private final EmailSender sender;
    private final ObjectMapper json;
    private final String mailHost;
    private final String appPublicBase;

    public EmailService(EmailPreferenceOperations preferences, OutboxConsumerOperations outbox, EmailSender sender,
            ObjectMapper json, @Value("${spring.mail.host:}") String mailHost,
            @Value("${mail.app-public-base:}") String appPublicBase) {
        this.preferences = preferences;
        this.outbox = outbox;
        this.sender = sender;
        this.json = json;
        this.mailHost = blankToEmpty(mailHost);
        this.appPublicBase = blankToEmpty(appPublicBase);
    }

    @Override
    @Transactional(readOnly = true)
    public EmailChannelStatus status(long userId) {
        var preference = preferences.status(userId);
        return new EmailChannelStatus(configured(), preference.enabled(), preference.address());
    }

    @Override
    public void setEnabled(long userId, boolean enabled) {
        preferences.setEnabled(userId, enabled);
    }

    @Override
    public int deliverPending(int limit) {
        if (!configured()) {
            return 0;
        }
        int delivered = 0;
        for (OutboxMessage message : outbox.claimEmailUnprocessed(limit)) {
            deliver(message);
            outbox.markEmailProcessed(message.id());
            delivered++;
        }
        return delivered;
    }

    private void deliver(OutboxMessage message) {
        if (!OutboxPushProjection.isPushEvent(message.eventType())) {
            return;
        }
        JsonNode payload = parse(message.payload());
        Set<Long> recipients = OutboxPushProjection.recipientIds(message.eventType(), payload);
        recipients.remove(OutboxPushProjection.actorId(message.eventType(), payload));
        if (recipients.isEmpty()) {
            return;
        }
        var addresses = preferences.enabledAddresses(recipients);
        var rendered = OutboxPushProjection.render(message.eventType(), payload, appPublicBase);
        for (Long userId : recipients) {
            String to = addresses.get(userId);
            if (to != null && !to.isBlank()) {
                sender.send(to, rendered.title(), rendered.body());
            }
        }
    }

    private boolean configured() {
        return !mailHost.isEmpty();
    }

    private JsonNode parse(String payload) {
        try {
            return json.readTree(payload);
        } catch (RuntimeException exception) {
            throw new EmailException(EmailException.Reason.VALIDATION_ERROR);
        }
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}

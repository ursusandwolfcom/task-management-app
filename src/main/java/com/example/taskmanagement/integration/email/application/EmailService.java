package com.example.taskmanagement.integration.email.application;

import com.example.taskmanagement.identity.api.EmailDigestMode;
import com.example.taskmanagement.identity.api.EmailPreferenceOperations;
import com.example.taskmanagement.identity.api.EmailPreferenceOperations.EmailTarget;
import com.example.taskmanagement.integration.email.api.EmailException;
import com.example.taskmanagement.integration.email.api.EmailOperations;
import com.example.taskmanagement.integration.email.api.EmailSender;
import com.example.taskmanagement.integration.email.persistence.EmailDigestEntryEntity;
import com.example.taskmanagement.integration.email.persistence.EmailDigestEntryRepository;
import com.example.taskmanagement.integration.outbox.api.OutboxConsumerOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxMessage;
import com.example.taskmanagement.integration.outbox.api.OutboxPushProjection;
import com.example.taskmanagement.integration.outbox.api.OutboxRecipientGuard;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
    private final EmailDigestEntryRepository digests;
    private final ObjectMapper json;
    private final Clock clock;
    private final String mailHost;
    private final String appPublicBase;
    private final String brand;
    private final EmailHtmlFormatter.Theme theme;
    private final OutboxRecipientGuard recipientGuard;

    public EmailService(EmailPreferenceOperations preferences, OutboxConsumerOperations outbox, EmailSender sender,
            EmailDigestEntryRepository digests, ObjectMapper json, Clock clock,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${mail.app-public-base:}") String appPublicBase,
            @Value("${mail.brand-name:ProjectFlow}") String brand,
            @Value("${mail.theme:default}") String theme, OutboxRecipientGuard recipientGuard) {
        this.preferences = preferences;
        this.outbox = outbox;
        this.sender = sender;
        this.digests = digests;
        this.json = json;
        this.clock = clock;
        this.recipientGuard = recipientGuard;
        this.mailHost = blankToEmpty(mailHost);
        this.appPublicBase = blankToEmpty(appPublicBase);
        this.brand = blankToEmpty(brand).isEmpty() ? "ProjectFlow" : blankToEmpty(brand);
        try {
            this.theme = EmailHtmlFormatter.Theme.from(theme);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("mail.theme must be default or slate", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EmailChannelStatus status(long userId) {
        var preference = preferences.status(userId);
        long pending = digests.countByUserIdIn(List.of(userId));
        return new EmailChannelStatus(configured(), preference.enabled(), preference.address(),
                preference.digestMode(), pending);
    }

    @Override
    public void setEnabled(long userId, boolean enabled) {
        preferences.setEnabled(userId, enabled);
    }

    @Override
    public void setDigestMode(long userId, EmailDigestMode mode) {
        if (mode == null) {
            throw new EmailException(EmailException.Reason.VALIDATION_ERROR);
        }
        preferences.setDigestMode(userId, mode);
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

    @Override
    public int flushDigests() {
        if (!configured()) {
            return 0;
        }
        int sent = 0;
        for (Long userId : digests.findDistinctUserIds()) {
            if (sendDigestForUser(userId)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean sendDigestForUser(long userId) {
        List<EmailDigestEntryEntity> entries = digests.findByUserIdOrderByIdAsc(userId);
        if (entries.isEmpty()) {
            return false;
        }
        // Keep the original selection: new rows committed during dispatch belong to the next digest.
        var selected = List.copyOf(entries);
        var preference = preferences.status(userId);
        if (!preference.enabled() || preference.address() == null || preference.address().isBlank()) {
            digests.deleteAll(selected);
            return false;
        }
        entries = entries.stream().filter(entry -> entry.outboxMessageId() == null
                || outbox.findMessage(entry.outboxMessageId())
                        .filter(message -> recipientGuard.permits(message, userId)).isPresent()).toList();
        if (entries.isEmpty()) {
            digests.deleteAll(selected);
            return false;
        }
        StringBuilder body = new StringBuilder("ProjectFlow — дайджест уведомлений\n\n");
        for (EmailDigestEntryEntity entry : entries) {
            body.append("• ").append(entry.subjectLine()).append('\n');
            for (String line : entry.bodyLine().split("\n")) {
                body.append("  ").append(line).append('\n');
            }
            body.append('\n');
        }
        String text = body.toString().strip();
        String subject = brand + ": дайджест (" + entries.size() + ")";
        dispatch(preference.address(), subject, text);
        digests.deleteAll(selected);
        return true;
    }

    private void deliver(OutboxMessage message) {
        if (!OutboxPushProjection.isPushEvent(message.eventType())) {
            return;
        }
        JsonNode payload = parse(message.payload());
        Set<Long> recipients = OutboxPushProjection.recipientIds(message.eventType(), payload);
        recipients.remove(OutboxPushProjection.actorId(message.eventType(), payload));
        recipients.removeIf(recipient -> !recipientGuard.permits(message, recipient));
        if (recipients.isEmpty()) {
            return;
        }
        var targets = preferences.enabledTargets(recipients);
        var rendered = OutboxPushProjection.render(message.eventType(), payload, appPublicBase);
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<EmailDigestEntryEntity> queued = new ArrayList<>();
        for (Long userId : recipients) {
            EmailTarget target = targets.get(userId);
            if (target == null || target.address() == null || target.address().isBlank()) {
                continue;
            }
            if (target.digestMode() == EmailDigestMode.DAILY) {
                queued.add(new EmailDigestEntryEntity(userId, truncate(rendered.title(), 200),
                        truncate(rendered.body(), 2000), now, message.id()));
            } else {
                dispatch(target.address(), rendered.title(), rendered.body());
            }
        }
        if (!queued.isEmpty()) {
            digests.saveAll(queued);
        }
    }

    private void dispatch(String to, String subject, String textBody) {
        sender.send(to, subject, textBody, EmailHtmlFormatter.wrap(brand, subject, textBody, theme));
    }

    private JsonNode parse(String payload) {
        try {
            return json.readTree(payload);
        } catch (RuntimeException exception) {
            throw new EmailException(EmailException.Reason.VALIDATION_ERROR);
        }
    }

    private boolean configured() {
        return !mailHost.isEmpty();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

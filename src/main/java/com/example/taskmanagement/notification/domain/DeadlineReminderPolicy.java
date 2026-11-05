package com.example.taskmanagement.notification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;

/** Calendar-day rules only; persistence, transport and clocks belong to the application. */
public final class DeadlineReminderPolicy {
    public enum Kind {
        DAY_BEFORE, DUE_DAY
    }

    public record Reminder(long recipientId, LocalDate dueDate, Kind kind) {
    }

    public List<Reminder> reminders(Instant now, ZoneId timezone, LocalDate dueDate,
            Long assigneeId, long ownerId, boolean eligible) {
        if (!eligible || dueDate == null || assigneeId == null) {
            return List.of();
        }
        var local = now.atZone(timezone);
        if (local.toLocalTime().isBefore(LocalTime.of(9, 0))) {
            return List.of();
        }
        Kind kind;
        if (dueDate.equals(local.toLocalDate())) {
            kind = Kind.DUE_DAY;
        } else if (dueDate.equals(local.toLocalDate().plusDays(1))) {
            kind = Kind.DAY_BEFORE;
        } else {
            return List.of();
        }
        var recipients = new LinkedHashSet<Long>();
        recipients.add(assigneeId);
        recipients.add(ownerId);
        return recipients.stream().map(id -> new Reminder(id, dueDate, kind)).toList();
    }
}

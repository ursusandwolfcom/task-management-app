package com.example.taskmanagement.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.notification.domain.DeadlineReminderPolicy.Kind;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeadlineReminderPolicyTest {
    private final DeadlineReminderPolicy policy = new DeadlineReminderPolicy();

    @Test
    void waitsUntilNineThenCatchesUpOnlyOnTheIntendedLocalDay() {
        var due = LocalDate.of(2026, 11, 6);
        var zone = ZoneId.of("Europe/Kyiv");
        assertThat(policy.reminders(Instant.parse("2026-11-05T06:59:59Z"), zone, due, 2L, 1, true)).isEmpty();
        var reminders = policy.reminders(Instant.parse("2026-11-05T07:00:00Z"), zone, due, 2L, 1, true);
        assertThat(reminders).extracting(DeadlineReminderPolicy.Reminder::recipientId).containsExactly(2L, 1L);
        assertThat(reminders).allMatch(item -> item.kind() == Kind.DAY_BEFORE);
        assertThat(policy.reminders(Instant.parse("2026-11-06T21:59:59Z"), zone, due, 2L, 1, true))
                .allMatch(item -> item.kind() == Kind.DUE_DAY).hasSize(2);
        assertThat(policy.reminders(Instant.parse("2026-11-06T22:00:00Z"), zone, due, 2L, 1, true)).isEmpty();
    }

    @Test
    void deduplicatesOwnerAndAssigneeAndSkipsIneligibleTasks() {
        var now = Instant.parse("2026-11-05T10:00:00Z");
        var date = LocalDate.of(2026, 11, 5);
        var utc = ZoneId.of("UTC");
        assertThat(policy.reminders(now, utc, date, 1L, 1, true)).hasSize(1);
        assertThat(policy.reminders(now, utc, date, null, 1, true)).isEmpty();
        assertThat(policy.reminders(now, utc, null, 2L, 1, true)).isEmpty();
        assertThat(policy.reminders(now, utc, date, 2L, 1, false)).isEmpty();
    }

    @Test
    void handlesDstAndDateLineUsingCalendarDaysRatherThanUtcDates() {
        for (var zone : List.of(ZoneId.of("Europe/Kyiv"), ZoneId.of("America/New_York"),
                ZoneId.of("Pacific/Kiritimati"), ZoneId.of("Pacific/Pago_Pago"))) {
            for (var date : List.of(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 29),
                    LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 1))) {
                var nine = date.atTime(9, 0).atZone(zone).toInstant();
                assertThat(policy.reminders(nine.minusSeconds(1), zone, date.plusDays(1), 2L, 1, true)).isEmpty();
                assertThat(policy.reminders(nine, zone, date.plusDays(1), 2L, 1, true))
                        .hasSize(2).allMatch(item -> item.kind() == Kind.DAY_BEFORE);
                assertThat(policy.reminders(nine, zone, date, 2L, 1, true))
                        .hasSize(2).allMatch(item -> item.kind() == Kind.DUE_DAY);
            }
        }
    }
}

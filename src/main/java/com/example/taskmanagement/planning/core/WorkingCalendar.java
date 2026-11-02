package com.example.taskmanagement.planning.core;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Fixed Mon–Fri 09:00–17:00 window in a project IANA zone. Intervals are [start, end).
 * Holidays and breaks are out of scope; resource contention is ignored.
 */
public final class WorkingCalendar {

    public static final LocalTime DAY_START = LocalTime.of(9, 0);
    public static final LocalTime DAY_END = LocalTime.of(17, 0);
    public static final int MINUTES_PER_DAY = 8 * 60;

    private final ZoneId zone;

    public WorkingCalendar(ZoneId zone) {
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public ZoneId zone() {
        return zone;
    }

    /** Project calendar origin: first working 09:00 on or after startDate in the project zone. */
    public Instant projectOrigin(LocalDate startDate) {
        Objects.requireNonNull(startDate, "startDate");
        return normalizeStart(ZonedDateTime.of(startDate, DAY_START, zone).toInstant());
    }

    /** Move an instant onto the next valid working instant (inclusive of day start). */
    public Instant normalizeStart(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        var local = instant.atZone(zone);
        if (!isWorkingDay(local.getDayOfWeek())) {
            return nextDayStart(local.toLocalDate().plusDays(1));
        }
        var time = local.toLocalTime();
        if (time.isBefore(DAY_START)) {
            return local.with(DAY_START).toInstant();
        }
        if (!time.isBefore(DAY_END)) {
            return nextDayStart(local.toLocalDate().plusDays(1));
        }
        return local.toInstant();
    }

    /** Adds positive working minutes; zero returns the normalized start. */
    public Instant addWorkingMinutes(Instant start, long workingMinutes) {
        if (workingMinutes < 0) {
            throw new IllegalArgumentException("Working minutes must be nonnegative");
        }
        var cursor = normalizeStart(start);
        long remaining = workingMinutes;
        while (remaining > 0) {
            var local = cursor.atZone(zone);
            long leftToday = Duration.between(local.toLocalTime(), DAY_END).toMinutes();
            if (remaining <= leftToday) {
                return local.plusMinutes(remaining).toInstant();
            }
            remaining -= leftToday;
            cursor = nextDayStart(local.toLocalDate().plusDays(1));
        }
        return cursor;
    }

    /** Whole working minutes in [start, end); both bounds are normalized as schedule points. */
    public long workingMinutesBetween(Instant start, Instant end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End is before start");
        }
        var cursor = normalizeStart(start);
        var limit = end;
        if (!cursor.isBefore(limit)) {
            return 0L;
        }
        long total = 0L;
        while (cursor.isBefore(limit)) {
            var local = cursor.atZone(zone);
            var dayEnd = local.with(DAY_END).toInstant();
            var sliceEnd = dayEnd.isBefore(limit) ? dayEnd : limit;
            total += Duration.between(cursor, sliceEnd).toMinutes();
            if (!sliceEnd.isBefore(dayEnd)) {
                cursor = nextDayStart(local.toLocalDate().plusDays(1));
            } else {
                break;
            }
        }
        return total;
    }

    private Instant nextDayStart(LocalDate date) {
        var day = date;
        while (!isWorkingDay(day.getDayOfWeek())) {
            day = day.plusDays(1);
        }
        return ZonedDateTime.of(day, DAY_START, zone).toInstant();
    }

    private static boolean isWorkingDay(DayOfWeek day) {
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }
}

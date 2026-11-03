package com.example.taskmanagement.planning.core;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * Mon–Fri 09:00–17:00 window in a project IANA zone, minus explicit holiday dates.
 * Intervals are [start, end). Breaks and resource contention are out of scope.
 */
public final class WorkingCalendar {

    public static final LocalTime DAY_START = LocalTime.of(9, 0);
    public static final LocalTime DAY_END = LocalTime.of(17, 0);
    public static final int MINUTES_PER_DAY = 8 * 60;

    private final ZoneId zone;
    private final Set<LocalDate> holidays;

    public WorkingCalendar(ZoneId zone) {
        this(zone, Set.of());
    }

    public WorkingCalendar(ZoneId zone, Set<LocalDate> holidays) {
        this.zone = Objects.requireNonNull(zone, "zone");
        this.holidays = Set.copyOf(Objects.requireNonNull(holidays, "holidays"));
    }

    public ZoneId zone() {
        return zone;
    }

    public Set<LocalDate> holidays() {
        return holidays;
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
        if (!isWorkingDay(local.toLocalDate())) {
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
        while (!isWorkingDay(day)) {
            day = day.plusDays(1);
        }
        return ZonedDateTime.of(day, DAY_START, zone).toInstant();
    }

    private boolean isWorkingDay(LocalDate day) {
        DayOfWeek week = day.getDayOfWeek();
        return week != DayOfWeek.SATURDAY && week != DayOfWeek.SUNDAY && !holidays.contains(day);
    }
}

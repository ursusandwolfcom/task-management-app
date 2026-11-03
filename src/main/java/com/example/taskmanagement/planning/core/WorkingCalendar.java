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
 * Mon–Fri 09:00–17:00 in a project IANA zone, minus holidays and a fixed lunch break
 * 13:00–14:00. Intervals are [start, end). Resource contention is out of scope.
 */
public final class WorkingCalendar {

    public static final LocalTime DAY_START = LocalTime.of(9, 0);
    public static final LocalTime BREAK_START = LocalTime.of(13, 0);
    public static final LocalTime BREAK_END = LocalTime.of(14, 0);
    public static final LocalTime DAY_END = LocalTime.of(17, 0);
    /** Net working minutes per weekday after the fixed lunch break. */
    public static final int MINUTES_PER_DAY = 7 * 60;

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
        if (!time.isBefore(BREAK_START) && time.isBefore(BREAK_END)) {
            return local.with(BREAK_END).toInstant();
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
            var segmentEnd = segmentEnd(local.toLocalTime());
            long leftInSegment = Duration.between(local.toLocalTime(), segmentEnd).toMinutes();
            if (remaining <= leftInSegment) {
                return local.plusMinutes(remaining).toInstant();
            }
            remaining -= leftInSegment;
            cursor = advancePastSegment(local, segmentEnd);
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
            var segmentEnd = segmentEnd(local.toLocalTime());
            var segmentEndInstant = local.with(segmentEnd).toInstant();
            var sliceEnd = segmentEndInstant.isBefore(limit) ? segmentEndInstant : limit;
            total += Duration.between(cursor, sliceEnd).toMinutes();
            if (!sliceEnd.isBefore(segmentEndInstant)) {
                cursor = advancePastSegment(local, segmentEnd);
            } else {
                break;
            }
        }
        return total;
    }

    private Instant advancePastSegment(ZonedDateTime local, LocalTime segmentEnd) {
        if (segmentEnd.equals(BREAK_START)) {
            return local.with(BREAK_END).toInstant();
        }
        return nextDayStart(local.toLocalDate().plusDays(1));
    }

    private static LocalTime segmentEnd(LocalTime time) {
        return time.isBefore(BREAK_START) ? BREAK_START : DAY_END;
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

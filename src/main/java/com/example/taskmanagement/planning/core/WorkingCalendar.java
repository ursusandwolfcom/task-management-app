package com.example.taskmanagement.planning.core;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Mon–Fri 09:00–17:00 in a project IANA zone, minus holidays and break windows.
 * Default break is lunch 13:00–14:00 (420 net minutes/day). Optional daily capacity
 * (FTE minutes) caps net working minutes per day for leveling. Intervals are [start, end).
 */
public final class WorkingCalendar {

    public static final LocalTime DAY_START = LocalTime.of(9, 0);
    public static final LocalTime BREAK_START = LocalTime.of(13, 0);
    public static final LocalTime BREAK_END = LocalTime.of(14, 0);
    public static final LocalTime DAY_END = LocalTime.of(17, 0);
    public static final List<BreakWindow> DEFAULT_BREAKS = List.of(new BreakWindow(BREAK_START, BREAK_END));
    /** Net working minutes per weekday after the default lunch break. */
    public static final int MINUTES_PER_DAY = 7 * 60;

    private final ZoneId zone;
    private final Set<LocalDate> holidays;
    private final List<BreakWindow> breaks;
    private final int minutesPerDay;
    private final int dailyCapacityMinutes;

    public WorkingCalendar(ZoneId zone) {
        this(zone, Set.of());
    }

    public WorkingCalendar(ZoneId zone, Set<LocalDate> holidays) {
        this(zone, holidays, DEFAULT_BREAKS);
    }

    public WorkingCalendar(ZoneId zone, Set<LocalDate> holidays, List<BreakWindow> breaks) {
        this(zone, holidays, breaks, null);
    }

    private WorkingCalendar(ZoneId zone, Set<LocalDate> holidays, List<BreakWindow> breaks, Integer dailyCapacity) {
        this.zone = Objects.requireNonNull(zone, "zone");
        this.holidays = Set.copyOf(Objects.requireNonNull(holidays, "holidays"));
        this.breaks = normalizeBreaks(Objects.requireNonNull(breaks, "breaks"));
        long dayMinutes = Duration.between(DAY_START, DAY_END).toMinutes();
        long breakMinutes = this.breaks.stream().mapToLong(BreakWindow::minutes).sum();
        if (breakMinutes >= dayMinutes) {
            throw new IllegalArgumentException("Breaks must leave positive working time in the day");
        }
        this.minutesPerDay = (int) (dayMinutes - breakMinutes);
        if (dailyCapacity == null) {
            this.dailyCapacityMinutes = this.minutesPerDay;
        } else {
            if (dailyCapacity < 1 || dailyCapacity > this.minutesPerDay) {
                throw new IllegalArgumentException("Daily capacity must lie within 1..minutesPerDay");
            }
            this.dailyCapacityMinutes = dailyCapacity;
        }
    }

    /** Copy with a per-day working-minute cap (FTE); must be 1..{@link #minutesPerDay()}. */
    public WorkingCalendar withDailyCapacityMinutes(int capacityMinutes) {
        return new WorkingCalendar(zone, holidays, breaks, capacityMinutes);
    }

    public ZoneId zone() {
        return zone;
    }

    public Set<LocalDate> holidays() {
        return holidays;
    }

    public List<BreakWindow> breaks() {
        return breaks;
    }

    public int minutesPerDay() {
        return minutesPerDay;
    }

    public int dailyCapacityMinutes() {
        return dailyCapacityMinutes;
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
            local = local.with(DAY_START);
            time = DAY_START;
        }
        for (BreakWindow window : breaks) {
            if (!time.isBefore(window.start()) && time.isBefore(window.end())) {
                local = local.with(window.end());
                time = window.end();
            }
        }
        if (!time.isBefore(DAY_END)) {
            return nextDayStart(local.toLocalDate().plusDays(1));
        }
        if (uncappedWorkedMinutes(local.toLocalDate(), time) >= dailyCapacityMinutes) {
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
            long usedToday = uncappedWorkedMinutes(local.toLocalDate(), local.toLocalTime());
            long leftToday = dailyCapacityMinutes - usedToday;
            if (leftToday <= 0) {
                cursor = nextDayStart(local.toLocalDate().plusDays(1));
                continue;
            }
            var segmentEnd = segmentEnd(local.toLocalTime());
            long leftInSegment = Duration.between(local.toLocalTime(), segmentEnd).toMinutes();
            long take = Math.min(remaining, Math.min(leftInSegment, leftToday));
            if (take == remaining) {
                return local.plusMinutes(take).toInstant();
            }
            remaining -= take;
            if (take == leftToday) {
                cursor = nextDayStart(local.toLocalDate().plusDays(1));
            } else {
                cursor = advancePastSegment(local, segmentEnd);
            }
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
            long usedToday = uncappedWorkedMinutes(local.toLocalDate(), local.toLocalTime());
            long leftToday = dailyCapacityMinutes - usedToday;
            if (leftToday <= 0) {
                cursor = nextDayStart(local.toLocalDate().plusDays(1));
                continue;
            }
            var segmentEnd = segmentEnd(local.toLocalTime());
            var segmentEndInstant = local.with(segmentEnd).toInstant();
            var capacityEnd = local.plusMinutes(leftToday).toInstant();
            var sliceEnd = segmentEndInstant.isBefore(limit) ? segmentEndInstant : limit;
            if (capacityEnd.isBefore(sliceEnd)) {
                sliceEnd = capacityEnd;
            }
            total += Duration.between(cursor, sliceEnd).toMinutes();
            if (sliceEnd.equals(capacityEnd) && capacityEnd.isBefore(segmentEndInstant)
                    && capacityEnd.isBefore(limit)) {
                cursor = nextDayStart(local.toLocalDate().plusDays(1));
            } else if (!sliceEnd.isBefore(segmentEndInstant)) {
                cursor = advancePastSegment(local, segmentEnd);
            } else {
                break;
            }
        }
        return total;
    }

    private long uncappedWorkedMinutes(LocalDate day, LocalTime time) {
        if (time.isBefore(DAY_START) || !isWorkingDay(day)) {
            return 0L;
        }
        LocalTime end = time.isAfter(DAY_END) ? DAY_END : time;
        long total = 0L;
        LocalTime cursor = DAY_START;
        while (cursor.isBefore(end)) {
            LocalTime segmentEnd = segmentEnd(cursor);
            LocalTime sliceEnd = segmentEnd.isBefore(end) ? segmentEnd : end;
            total += Duration.between(cursor, sliceEnd).toMinutes();
            if (!sliceEnd.isBefore(segmentEnd) && sliceEnd.isBefore(end)) {
                BreakWindow matched = null;
                for (BreakWindow window : breaks) {
                    if (segmentEnd.equals(window.start())) {
                        matched = window;
                        break;
                    }
                }
                cursor = matched == null ? end : matched.end();
            } else {
                break;
            }
        }
        return total;
    }

    private Instant advancePastSegment(ZonedDateTime local, LocalTime segmentEnd) {
        for (BreakWindow window : breaks) {
            if (segmentEnd.equals(window.start())) {
                return local.with(window.end()).toInstant();
            }
        }
        return nextDayStart(local.toLocalDate().plusDays(1));
    }

    private LocalTime segmentEnd(LocalTime time) {
        for (BreakWindow window : breaks) {
            if (time.isBefore(window.start())) {
                return window.start();
            }
        }
        return DAY_END;
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

    public static List<BreakWindow> normalizeBreaks(List<BreakWindow> input) {
        var sorted = input.stream().sorted(Comparator.comparing(BreakWindow::start)).toList();
        List<BreakWindow> normalized = new ArrayList<>(sorted.size());
        LocalTime previousEnd = null;
        for (BreakWindow window : sorted) {
            if (previousEnd != null && window.start().isBefore(previousEnd)) {
                throw new IllegalArgumentException("Break windows must not overlap");
            }
            normalized.add(window);
            previousEnd = window.end();
        }
        return List.copyOf(normalized);
    }
}

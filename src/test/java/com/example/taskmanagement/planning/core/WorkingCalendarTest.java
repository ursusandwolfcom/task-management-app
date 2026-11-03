package com.example.taskmanagement.planning.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkingCalendarTest {

    private final WorkingCalendar calendar = new WorkingCalendar(ZoneId.of("UTC"));

    @Test
    void weekendAndClosingTimeMoveToNextWorkingMorning() {
        var fridayClose = ZonedDateTime.of(2026, 11, 6, 17, 0, 0, 0, ZoneId.of("UTC")).toInstant();
        assertEquals(ZonedDateTime.of(2026, 11, 9, 9, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
                calendar.normalizeStart(fridayClose));
        var saturday = ZonedDateTime.of(2026, 11, 7, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant();
        assertEquals(ZonedDateTime.of(2026, 11, 9, 9, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
                calendar.normalizeStart(saturday));
    }

    @Test
    void lunchBreakIsSkippedAndShortensNetDay() {
        assertEquals(ZonedDateTime.of(2026, 11, 2, 14, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
                calendar.normalizeStart(
                        ZonedDateTime.of(2026, 11, 2, 13, 30, 0, 0, ZoneId.of("UTC")).toInstant()));
        Instant noon = ZonedDateTime.of(2026, 11, 2, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant();
        // Morning segment ends at 13:00 (exclusive); the next working minute is 14:00.
        assertEquals(ZonedDateTime.of(2026, 11, 2, 13, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
                calendar.addWorkingMinutes(noon, 60));
        assertEquals(ZonedDateTime.of(2026, 11, 2, 14, 1, 0, 0, ZoneId.of("UTC")).toInstant(),
                calendar.addWorkingMinutes(noon, 61));
        assertEquals(120L, calendar.workingMinutesBetween(noon,
                ZonedDateTime.of(2026, 11, 2, 15, 0, 0, 0, ZoneId.of("UTC")).toInstant()));
        assertEquals(420, WorkingCalendar.MINUTES_PER_DAY);
    }

    @Test
    void fullWorkingDayAndCrossWeekendAddition() {
        Instant monday = calendar.projectOrigin(LocalDate.of(2026, 11, 2));
        assertEquals(ZonedDateTime.of(2026, 11, 2, 17, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
                calendar.addWorkingMinutes(monday, WorkingCalendar.MINUTES_PER_DAY));
        // Five full working days from Monday 09:00 end Friday 17:00.
        assertEquals(ZonedDateTime.of(2026, 11, 6, 17, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
                calendar.addWorkingMinutes(monday, WorkingCalendar.MINUTES_PER_DAY * 5));
        // Finish exactly at Friday 17:00; the next working minute is Monday 09:01.
        assertEquals(ZonedDateTime.of(2026, 11, 9, 9, 1, 0, 0, ZoneId.of("UTC")).toInstant(),
                calendar.addWorkingMinutes(monday, WorkingCalendar.MINUTES_PER_DAY * 5 + 1));
        assertEquals(WorkingCalendar.MINUTES_PER_DAY,
                calendar.workingMinutesBetween(monday,
                        ZonedDateTime.of(2026, 11, 2, 17, 0, 0, 0, ZoneId.of("UTC")).toInstant()));
    }

    @Test
    void timezoneKeepsCivilWallClockAcrossDst() {
        var kyiv = new WorkingCalendar(ZoneId.of("Europe/Kyiv"));
        // Sunday project start snaps to Monday after EU DST spring-forward.
        Instant start = kyiv.projectOrigin(LocalDate.of(2026, 3, 29));
        assertEquals(ZonedDateTime.of(2026, 3, 30, 9, 0, 0, 0, ZoneId.of("Europe/Kyiv")).toInstant(), start);
        assertEquals(ZonedDateTime.of(2026, 3, 30, 17, 0, 0, 0, ZoneId.of("Europe/Kyiv")).toInstant(),
                kyiv.addWorkingMinutes(start, WorkingCalendar.MINUTES_PER_DAY));
    }

    @Test
    void weekdayHolidayIsSkippedLikeWeekend() {
        var withHoliday = new WorkingCalendar(ZoneId.of("UTC"), Set.of(LocalDate.of(2026, 11, 4)));
        Instant monday = withHoliday.projectOrigin(LocalDate.of(2026, 11, 2));
        // Mon+Tue full days; Wednesday is holiday → finish Thursday 17:00 after third day.
        assertEquals(ZonedDateTime.of(2026, 11, 5, 17, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
                withHoliday.addWorkingMinutes(monday, WorkingCalendar.MINUTES_PER_DAY * 3));
        assertEquals(ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
                withHoliday.normalizeStart(
                        ZonedDateTime.of(2026, 11, 4, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant()));
    }

    @Test
    void customBreakWindowsShortenNetDay() {
        var shortLunch = new WorkingCalendar(ZoneId.of("UTC"), Set.of(),
                List.of(new BreakWindow(LocalTime.of(12, 0), LocalTime.of(12, 30))));
        assertEquals(450, shortLunch.minutesPerDay());
        Instant morning = ZonedDateTime.of(2026, 11, 2, 11, 0, 0, 0, ZoneId.of("UTC")).toInstant();
        assertEquals(ZonedDateTime.of(2026, 11, 2, 12, 30, 0, 0, ZoneId.of("UTC")).toInstant(),
                shortLunch.normalizeStart(
                        ZonedDateTime.of(2026, 11, 2, 12, 15, 0, 0, ZoneId.of("UTC")).toInstant()));
        assertEquals(ZonedDateTime.of(2026, 11, 2, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
                shortLunch.addWorkingMinutes(morning, 60));
        assertEquals(ZonedDateTime.of(2026, 11, 2, 12, 31, 0, 0, ZoneId.of("UTC")).toInstant(),
                shortLunch.addWorkingMinutes(morning, 61));
    }

    @Test
    void emptyBreakListMeansFullEightHourDay() {
        var open = new WorkingCalendar(ZoneId.of("UTC"), Set.of(), List.of());
        assertEquals(480, open.minutesPerDay());
        Instant monday = open.projectOrigin(LocalDate.of(2026, 11, 2));
        assertEquals(ZonedDateTime.of(2026, 11, 2, 17, 0, 0, 0, ZoneId.of("UTC")).toInstant(),
                open.addWorkingMinutes(monday, 480));
    }

    @Test
    void fullDayBreakIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WorkingCalendar(ZoneId.of("UTC"), Set.of(),
                List.of(new BreakWindow(LocalTime.of(9, 0), LocalTime.of(17, 0)))));
    }
}

package com.example.taskmanagement.planning.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
}

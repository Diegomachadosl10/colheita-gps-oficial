package com.colheitagps.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DailyVerseTest {
    @Test fun theCalendarDayKeepsTheVerseStableAndChangesAtMidnightIncludingYearRollover() {
        val date=LocalDate.of(2026,12,31)
        assertEquals(DailyVerse.forDate(date),DailyVerse.forDate(date))
        assertNotEquals(DailyVerse.forDate(date),DailyVerse.forDate(date.plusDays(1)))
        assertEquals(31,(0L..30L).map { DailyVerse.forDate(date.plusDays(it)).reference }.toSet().size)
    }
}

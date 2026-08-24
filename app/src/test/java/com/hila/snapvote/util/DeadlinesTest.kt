package com.hila.snapvote.util

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The date dialog reports UTC midnight and the clock dialog reports local time, so
 * joining them is the one place in the custom deadline where a timezone can quietly
 * move the poll to the wrong day. These tests pin the day down in a zone ahead of UTC
 * and in one behind it.
 */
class DeadlinesTest {

    private val jerusalem = TimeZone.getTimeZone("Asia/Jerusalem")
    private val newYork = TimeZone.getTimeZone("America/New_York")

    /** What MaterialDatePicker returns for a chosen day: midnight of that day in UTC. */
    private fun utcMidnight(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis

    private fun fieldsIn(zone: TimeZone, millis: Long): Calendar =
        Calendar.getInstance(zone).apply { timeInMillis = millis }

    @Test
    fun `keeps the chosen day and time in a zone ahead of UTC`() {
        val result = Deadlines.combine(
            utcMidnight(2026, Calendar.AUGUST, 26), hour = 14, minute = 30, zone = jerusalem
        )

        val cal = fieldsIn(jerusalem, result.time)
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.AUGUST, cal.get(Calendar.MONTH))
        assertEquals(26, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `does not slip to the previous day in a zone behind UTC`() {
        // 01:00 is the hour that would fall back to the 25th if the UTC day were read
        // in local time instead of in UTC.
        val result = Deadlines.combine(
            utcMidnight(2026, Calendar.AUGUST, 26), hour = 1, minute = 0, zone = newYork
        )

        val cal = fieldsIn(newYork, result.time)
        assertEquals(26, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(1, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `keeps a late evening time on the same day`() {
        val result = Deadlines.combine(
            utcMidnight(2026, Calendar.DECEMBER, 31), hour = 23, minute = 59, zone = jerusalem
        )

        val cal = fieldsIn(jerusalem, result.time)
        assertEquals(Calendar.DECEMBER, cal.get(Calendar.MONTH))
        assertEquals(31, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `drops seconds and milliseconds so the deadline is a round minute`() {
        val result = Deadlines.combine(
            utcMidnight(2026, Calendar.AUGUST, 26), hour = 9, minute = 5, zone = jerusalem
        )

        val cal = fieldsIn(jerusalem, result.time)
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `midnight is a legal choice and stays on the chosen day`() {
        val result = Deadlines.combine(
            utcMidnight(2026, Calendar.AUGUST, 26), hour = 0, minute = 0, zone = jerusalem
        )

        val cal = fieldsIn(jerusalem, result.time)
        assertEquals(26, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
    }
}

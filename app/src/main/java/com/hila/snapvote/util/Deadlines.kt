package com.hila.snapvote.util

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Turning what the two deadline dialogs report into one moment in time.
 *
 * `MaterialDatePicker` hands back **UTC midnight** of the chosen day, while
 * `MaterialTimePicker` hands back an hour and a minute meant in local time. Joining
 * them naively reads the day in the phone's own zone, which lands on the previous day
 * for anyone west of UTC. Keeping the arithmetic here – with the zone as a parameter –
 * is what makes it testable without a device.
 */
object Deadlines {

    /**
     * Combines [dayUtcMillis] (UTC midnight of the chosen day) with a local [hour] and
     * [minute]. Seconds and milliseconds are dropped, so the deadline is a round minute.
     */
    fun combine(
        dayUtcMillis: Long,
        hour: Int,
        minute: Int,
        zone: TimeZone = TimeZone.getDefault(),
    ): Date {
        val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = dayUtcMillis
        }
        val combined = Calendar.getInstance(zone).apply {
            clear()
            set(
                day.get(Calendar.YEAR),
                day.get(Calendar.MONTH),
                day.get(Calendar.DAY_OF_MONTH),
                hour,
                minute,
                0,
            )
        }
        return combined.time
    }
}

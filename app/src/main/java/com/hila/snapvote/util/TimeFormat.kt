package com.hila.snapvote.util

import android.content.Context
import com.hila.snapvote.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Human friendly "⏳ נותרו 5 שעות" text for a poll deadline. */
object TimeFormat {

    /** "26/08 14:30" – the label the custom deadline chip shows once a time is picked. */
    fun dateTime(at: Date): String =
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(at)

    fun timeLeft(context: Context, millisLeft: Long): String = when {
        millisLeft <= 0 -> context.getString(R.string.poll_closed)
        millisLeft >= TimeUnit.DAYS.toMillis(1) ->
            context.getString(R.string.time_left_days, TimeUnit.MILLISECONDS.toDays(millisLeft).toInt())
        millisLeft >= TimeUnit.HOURS.toMillis(1) ->
            context.getString(R.string.time_left_hours, TimeUnit.MILLISECONDS.toHours(millisLeft).toInt())
        else ->
            context.getString(
                R.string.time_left_minutes,
                TimeUnit.MILLISECONDS.toMinutes(millisLeft).coerceAtLeast(1).toInt()
            )
    }
}

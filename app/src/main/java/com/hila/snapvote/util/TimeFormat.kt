package com.hila.snapvote.util

import android.content.Context
import com.hila.snapvote.R
import java.util.concurrent.TimeUnit

/** Human friendly "⏳ נותרו 5 שעות" text for a poll deadline. */
object TimeFormat {

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

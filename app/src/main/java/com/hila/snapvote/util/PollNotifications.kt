package com.hila.snapvote.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hila.snapvote.R
import com.hila.snapvote.work.PollDeadlineWorker
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Schedules the two reminders that belong to a poll: one an hour before it closes
 * and one the moment it does. Everything runs on the device through WorkManager,
 * so no server (and no paid plan) is involved.
 */
object PollNotifications {

    const val CHANNEL_ID = "poll_deadlines"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun scheduleFor(context: Context, pollId: String, question: String, deadline: Date) {
        val millisLeft = deadline.time - System.currentTimeMillis()
        if (millisLeft <= 0) return

        val hourBefore = millisLeft - TimeUnit.HOURS.toMillis(1)
        if (hourBefore > 0) {
            enqueue(context, pollId, question, hourBefore, PollDeadlineWorker.KIND_REMINDER)
        }
        enqueue(context, pollId, question, millisLeft, PollDeadlineWorker.KIND_CLOSED)
    }

    /** Called when a poll is deleted, so no reminder fires for something that is gone. */
    fun cancelFor(context: Context, pollId: String) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(workName(pollId, PollDeadlineWorker.KIND_REMINDER))
        work.cancelUniqueWork(workName(pollId, PollDeadlineWorker.KIND_CLOSED))
    }

    private fun enqueue(
        context: Context,
        pollId: String,
        question: String,
        delayMillis: Long,
        kind: String,
    ) {
        val request = OneTimeWorkRequestBuilder<PollDeadlineWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(PollDeadlineWorker.KEY_POLL_ID, pollId)
                    .putString(PollDeadlineWorker.KEY_QUESTION, question)
                    .putString(PollDeadlineWorker.KEY_KIND, kind)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(pollId, kind),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun workName(pollId: String, kind: String) = "poll_${pollId}_$kind"
}

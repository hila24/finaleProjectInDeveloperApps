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
 * Schedules the two reminders that belong to a poll: one shortly before it closes
 * and one the moment it does. Everything runs on the device through WorkManager,
 * so no server (and no paid plan) is involved.
 *
 * WorkManager trades exact timing for battery life, so a reminder can run a little
 * late. With a short lead time that matters – see the staleness check in
 * [PollDeadlineWorker].
 */
object PollNotifications {

    const val CHANNEL_ID = "poll_deadlines"

    /** Shared by every scheduled reminder, so logout can drop them all at once. */
    private const val WORK_TAG = "poll_deadline"

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

    /** How long before the deadline the "last chance" reminder goes out. */
    private val REMINDER_LEAD_MILLIS = TimeUnit.MINUTES.toMillis(5)

    /**
     * Books the two reminders for a poll on **this** device.
     *
     * Both the poll's author and anyone who sees it in their feed call this, each for
     * their own phone – that is what lets friends be reminded without a server.
     *
     * [withReminder] is false for someone who has already voted: the closing result is
     * still worth a notification, but "hurry up and vote" no longer is.
     *
     * [ownerId] is passed only by the poll's author, on their own phone. It lets the
     * closing job also delete the pictures at the deadline instead of waiting for their
     * next visit to the app – nobody else is allowed to delete them.
     */
    fun scheduleFor(
        context: Context,
        pollId: String,
        question: String,
        deadline: Date,
        withReminder: Boolean = true,
        ownerId: String? = null,
    ) {
        val millisLeft = deadline.time - System.currentTimeMillis()
        if (millisLeft <= 0) return

        // A poll shorter than the lead time gets the closing notification only.
        val beforeClosing = millisLeft - REMINDER_LEAD_MILLIS
        if (withReminder && beforeClosing > 0) {
            enqueue(
                context, pollId, question, beforeClosing,
                PollDeadlineWorker.KIND_REMINDER, deadline.time, ownerId = null
            )
        }
        enqueue(
            context, pollId, question, millisLeft,
            PollDeadlineWorker.KIND_CLOSED, deadline.time, ownerId
        )
    }

    /** Called when a poll is deleted, so no reminder fires for something that is gone. */
    fun cancelFor(context: Context, pollId: String) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(workName(pollId, PollDeadlineWorker.KIND_REMINDER))
        work.cancelUniqueWork(workName(pollId, PollDeadlineWorker.KIND_CLOSED))
    }

    /**
     * Drops the "hurry up and vote" reminder but keeps the closing one. Called the
     * moment a vote is cast – there is nothing left to hurry for, but the result is
     * still worth hearing about.
     */
    fun cancelReminderFor(context: Context, pollId: String) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(workName(pollId, PollDeadlineWorker.KIND_REMINDER))
    }

    /**
     * Drops every pending reminder on this device. Called on logout: the tasks belong
     * to the phone rather than to the account, so without this the next person to sign
     * in here keeps receiving reminders for the previous user's polls – and tapping one
     * opens a poll they may not be allowed to read.
     */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    private fun enqueue(
        context: Context,
        pollId: String,
        question: String,
        delayMillis: Long,
        kind: String,
        deadlineMillis: Long,
        ownerId: String?,
    ) {
        val request = OneTimeWorkRequestBuilder<PollDeadlineWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .setInputData(
                Data.Builder()
                    .putString(PollDeadlineWorker.KEY_POLL_ID, pollId)
                    .putString(PollDeadlineWorker.KEY_QUESTION, question)
                    .putString(PollDeadlineWorker.KEY_KIND, kind)
                    .putLong(PollDeadlineWorker.KEY_DEADLINE, deadlineMillis)
                    .apply { if (ownerId != null) putString(PollDeadlineWorker.KEY_OWNER_ID, ownerId) }
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

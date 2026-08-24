package com.hila.snapvote.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hila.snapvote.MainActivity
import com.hila.snapvote.R
import com.hila.snapvote.util.PollNotifications

/**
 * Posts the "your poll closes in 5 minutes" and "your poll is closed" notifications.
 * Tapping one opens the poll through the same deep link that is used for sharing.
 */
class PollDeadlineWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val pollId = inputData.getString(KEY_POLL_ID) ?: return Result.failure()
        val question = inputData.getString(KEY_QUESTION).orEmpty()
        val kind = inputData.getString(KEY_KIND) ?: KIND_CLOSED
        val deadline = inputData.getLong(KEY_DEADLINE, 0L)

        // WorkManager may run this later than asked, to save battery. The reminder only
        // has a few minutes of lead, so a late run would announce "5 minutes left" for a
        // poll that has already closed – and arrive after the closing notification.
        if (kind == KIND_REMINDER && deadline > 0L && System.currentTimeMillis() >= deadline) {
            return Result.success()
        }

        val granted = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return Result.success()

        val title = applicationContext.getString(
            if (kind == KIND_REMINDER) R.string.notify_reminder_title else R.string.notify_closed_title
        )
        val text = applicationContext.getString(
            if (kind == KIND_REMINDER) R.string.notify_reminder_text else R.string.notify_closed_text,
            question
        )

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://snapvote-hila-2026.web.app/poll/$pollId"),
            applicationContext,
            MainActivity::class.java
        )
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            pollId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, PollNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify((pollId + kind).hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_POLL_ID = "pollId"
        const val KEY_QUESTION = "question"
        const val KEY_KIND = "kind"
        const val KEY_DEADLINE = "deadline"
        const val KIND_REMINDER = "reminder"
        const val KIND_CLOSED = "closed"
    }
}

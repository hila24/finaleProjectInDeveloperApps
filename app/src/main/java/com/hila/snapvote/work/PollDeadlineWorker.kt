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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.hila.snapvote.MainActivity
import com.hila.snapvote.R
import com.hila.snapvote.data.repository.PollRepository
import com.hila.snapvote.util.PollNotifications

/**
 * Posts the "your poll closes in 5 minutes" and "your poll is closed" notifications,
 * and – on the poll owner's own device – deletes the pictures at the deadline.
 * Tapping a notification opens the poll through the same deep link used for sharing.
 */
class PollDeadlineWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pollId = inputData.getString(KEY_POLL_ID) ?: return Result.failure()
        val question = inputData.getString(KEY_QUESTION).orEmpty()
        val kind = inputData.getString(KEY_KIND) ?: KIND_CLOSED
        val deadline = inputData.getLong(KEY_DEADLINE, 0L)

        if (kind == KIND_CLOSED) deletePicturesIfOwner()

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

    /**
     * Wipes the pictures of every finished poll belonging to whoever is signed in here.
     *
     * Only the owner may delete them, so this runs only when the account on this device
     * is still the one that scheduled the work – the uid is compared at run time rather
     * than trusted from the input, because a phone can change hands between scheduling
     * and firing.
     *
     * This closes most of the gap between the deadline and the owner's next visit, but
     * it does not replace the sweep the feed does: WorkManager can be delayed by Doze,
     * and a force-stop drops pending work entirely. Both paths are idempotent.
     */
    private suspend fun deletePicturesIfOwner() {
        val ownerId = inputData.getString(KEY_OWNER_ID) ?: return
        if (FirebaseAuth.getInstance().currentUser?.uid != ownerId) return
        runCatching { PollRepository().cleanupExpiredPollsOf(ownerId) }
    }

    companion object {
        const val KEY_POLL_ID = "pollId"
        const val KEY_QUESTION = "question"
        const val KEY_KIND = "kind"
        const val KEY_DEADLINE = "deadline"
        /** Set only on the owner's own device; absent for a friend's copy of the work. */
        const val KEY_OWNER_ID = "ownerId"
        const val KIND_REMINDER = "reminder"
        const val KIND_CLOSED = "closed"
    }
}

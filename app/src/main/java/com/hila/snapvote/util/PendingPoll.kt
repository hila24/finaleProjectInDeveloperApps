package com.hila.snapvote.util

import android.content.Context

/**
 * A poll id that arrived from a shared link while nobody was signed in.
 * It is opened right after the login or registration succeeds.
 */
object PendingPoll {

    private const val PREFS = "snapvote_prefs"
    private const val KEY = "pending_poll_id"

    fun save(context: Context, pollId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, pollId).apply()
    }

    /** Returns the stored id once and forgets it. */
    fun consume(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pollId = prefs.getString(KEY, null)
        if (pollId != null) prefs.edit().remove(KEY).apply()
        return pollId
    }
}

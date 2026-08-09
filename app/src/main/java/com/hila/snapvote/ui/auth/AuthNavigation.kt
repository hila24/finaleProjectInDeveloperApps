package com.hila.snapvote.ui.auth

import android.content.Context
import androidx.navigation.NavController
import com.hila.snapvote.R
import com.hila.snapvote.ui.common.pollArgs
import com.hila.snapvote.util.PendingPoll

/**
 * After signing in, go to the feed – unless the app was opened from a shared poll
 * link, in which case that poll is what the user actually came for.
 */
fun openFeedOrPendingPoll(navController: NavController, context: Context) {
    navController.navigate(R.id.action_global_feed)

    val pollId = PendingPoll.consume(context) ?: return
    navController.navigate(R.id.voteFragment, pollArgs(pollId))
}

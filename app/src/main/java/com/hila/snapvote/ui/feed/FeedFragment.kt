package com.hila.snapvote.ui.feed

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.hila.snapvote.R
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.databinding.FragmentFeedBinding
import com.hila.snapvote.ui.common.BaseFragment
import com.hila.snapvote.ui.common.pollArgs

/** The home screen: every open poll of mine or of a friend, soonest to close first. */
class FeedFragment : BaseFragment<FragmentFeedBinding>(FragmentFeedBinding::inflate) {

    private val viewModel: FeedViewModel by viewModels()
    private val adapter by lazy { PollAdapter(onClick = ::openPoll) }

    /** Reminders are a bonus – the feed works the same either way. */
    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* nothing to undo if it is refused */ }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pollsList.layoutManager = LinearLayoutManager(requireContext())
        binding.pollsList.adapter = adapter

        binding.newPollButton.setOnClickListener {
            navigateSafely(R.id.action_feed_to_create)
        }

        // Firestore pushes updates on its own; the gesture is just for reassurance.
        binding.swipeRefresh.setOnRefreshListener { binding.swipeRefresh.isRefreshing = false }

        viewModel.activePolls.observe(viewLifecycleOwner) { polls ->
            adapter.submitList(polls)
            binding.emptyState.isVisible = polls.isEmpty()
        }
        viewModel.loading.observe(viewLifecycleOwner) { binding.progress.isVisible = it }
        viewModel.votedPollIds.observe(viewLifecycleOwner) { voted ->
            adapter.votedPollIds = voted
            // Both lists are in place by now, so this is where reminders can be booked.
            viewModel.scheduleReminders(requireContext().applicationContext)
        }
        observeMessage(viewModel.error, viewModel::errorShown)

        ensureNotificationPermission()
    }

    /**
     * Asked here and not only when publishing: someone who just votes on friends' polls
     * never opens the create screen, and without the permission their reminders would be
     * scheduled and then silently dropped.
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (viewModel.askedForNotifications) return
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return

        viewModel.askedForNotifications = true
        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Already voted, or the poll is over? Go straight to the results. */
    private fun openPoll(poll: Poll) {
        val alreadyVoted = poll.id in (viewModel.votedPollIds.value ?: emptySet())
        val action = if (alreadyVoted || poll.isClosed) {
            R.id.action_feed_to_results
        } else {
            R.id.action_feed_to_vote
        }
        navigateSafely(action, pollArgs(poll.id))
    }

    override fun onDestroyView() {
        binding.pollsList.adapter = null
        super.onDestroyView()
    }
}

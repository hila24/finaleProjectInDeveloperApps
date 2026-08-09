package com.hila.snapvote.ui.feed

import android.os.Bundle
import android.view.View
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
        viewModel.votedPollIds.observe(viewLifecycleOwner) { adapter.votedPollIds = it }
        observeMessage(viewModel.error, viewModel::errorShown)
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

package com.hila.snapvote.ui.profile

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.hila.snapvote.R
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.databinding.FragmentProfileBinding
import com.hila.snapvote.ui.common.BaseFragment
import com.hila.snapvote.ui.common.pollArgs
import com.hila.snapvote.ui.feed.PollAdapter

/**
 * Profile card, the counters, and two histories: the polls I created and the
 * finished polls of friends I voted on.
 */
class ProfileFragment : BaseFragment<FragmentProfileBinding>(FragmentProfileBinding::inflate) {

    private val viewModel: ProfileViewModel by viewModels()
    private val adapter by lazy { PollAdapter(onClick = ::openResults) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.myPollsList.adapter = adapter
        binding.historyGroup.setOnCheckedStateChangeListener { _, _ -> showSelectedHistory() }
        binding.logoutButton.setOnClickListener {
            viewModel.logout(requireContext().applicationContext)
            navigateSafely(R.id.action_global_login)
        }
        binding.friendsCard.setOnClickListener {
            navigateSafely(R.id.action_profile_to_friends)
        }

        viewModel.friendsCount.observe(viewLifecycleOwner) {
            binding.friendsCount.text = it.toString()
        }
        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user == null) return@observe
            binding.username.text = user.username
            binding.email.text = user.email
            binding.avatar.text = user.username.take(1).uppercase()
            binding.pollsCreated.text = user.pollsCreated.toString()
            binding.votesGiven.text = user.votesGiven.toString()
        }
        viewModel.myPolls.observe(viewLifecycleOwner) { showSelectedHistory() }
        viewModel.archivedPolls.observe(viewLifecycleOwner) { showSelectedHistory() }
        observeMessage(viewModel.error, viewModel::errorShown)
    }

    /**
     * Both histories share one list, so the chip decides what is on screen.
     * Called from the chip listener and from either list arriving.
     */
    private fun showSelectedHistory() {
        val showArchive = binding.historyGroup.checkedChipId == R.id.historyArchive
        val polls =
            if (showArchive) viewModel.archivedPolls.value else viewModel.myPolls.value

        adapter.submitList(polls.orEmpty())
        binding.historyEmpty.setText(
            if (showArchive) R.string.archive_empty else R.string.my_polls_empty
        )
        binding.historyEmpty.isVisible = polls != null && polls.isEmpty()
    }

    private fun openResults(poll: Poll) {
        navigateSafely(R.id.action_profile_to_results, pollArgs(poll.id))
    }

    override fun onDestroyView() {
        binding.myPollsList.adapter = null
        super.onDestroyView()
    }
}

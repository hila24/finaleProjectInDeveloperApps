package com.hila.snapvote.ui.profile

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.hila.snapvote.R
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.databinding.FragmentProfileBinding
import com.hila.snapvote.ui.common.BaseFragment
import com.hila.snapvote.ui.common.pollArgs
import com.hila.snapvote.ui.feed.PollAdapter

/** Profile card, the counters and the history of polls I created. */
class ProfileFragment : BaseFragment<FragmentProfileBinding>(FragmentProfileBinding::inflate) {

    private val viewModel: ProfileViewModel by viewModels()
    private val adapter by lazy { PollAdapter(onClick = ::openResults) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.myPollsList.adapter = adapter
        binding.logoutButton.setOnClickListener {
            viewModel.logout()
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
        viewModel.myPolls.observe(viewLifecycleOwner) { adapter.submitList(it) }
        observeMessage(viewModel.error, viewModel::errorShown)
    }

    private fun openResults(poll: Poll) {
        navigateSafely(R.id.action_profile_to_results, pollArgs(poll.id))
    }

    override fun onDestroyView() {
        binding.myPollsList.adapter = null
        super.onDestroyView()
    }
}

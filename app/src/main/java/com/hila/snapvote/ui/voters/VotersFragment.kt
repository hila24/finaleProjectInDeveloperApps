package com.hila.snapvote.ui.voters

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.hila.snapvote.databinding.FragmentVotersBinding
import com.hila.snapvote.ui.common.BaseFragment
import com.hila.snapvote.ui.common.pollIdArgument

/** Who voted in one poll, and what each of them picked. Owner-only screen. */
class VotersFragment : BaseFragment<FragmentVotersBinding>(FragmentVotersBinding::inflate) {

    private val viewModel: VotersViewModel by viewModels()
    private val pollId: String by lazy { pollIdArgument() }
    private val adapter = VoterAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.votersList.adapter = adapter
        binding.backButton.setOnClickListener { findNavController().popBackStack() }

        viewModel.load(pollId)

        viewModel.poll.observe(viewLifecycleOwner) { poll ->
            if (poll == null) return@observe
            binding.question.text = poll.question
            adapter.poll = poll
        }
        viewModel.votes.observe(viewLifecycleOwner) { votes ->
            adapter.submitList(votes)
            binding.emptyState.isVisible = votes.isEmpty()
        }
        viewModel.loading.observe(viewLifecycleOwner) { binding.progress.isVisible = it }
        observeMessage(viewModel.error, viewModel::errorShown)
    }

    override fun onDestroyView() {
        binding.votersList.adapter = null
        super.onDestroyView()
    }
}

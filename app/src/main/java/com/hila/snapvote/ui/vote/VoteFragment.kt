package com.hila.snapvote.ui.vote

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.hila.snapvote.R
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.databinding.FragmentVoteBinding
import com.hila.snapvote.ui.common.BaseFragment
import com.hila.snapvote.ui.common.pollArgs
import com.hila.snapvote.ui.common.pollIdArgument
import com.hila.snapvote.util.TimeFormat

/** Pick one image, or rate them all – depending on how the poll was created. */
class VoteFragment : BaseFragment<FragmentVoteBinding>(FragmentVoteBinding::inflate) {

    private val viewModel: VoteViewModel by viewModels()
    private val pollId: String by lazy { pollIdArgument() }
    private var adapter: VoteImagesAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.imagesList.layoutManager = LinearLayoutManager(requireContext())
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.sendVoteButton.setOnClickListener {
            viewModel.submit(requireContext().applicationContext, pollId)
        }

        viewModel.load(pollId)

        viewModel.poll.observe(viewLifecycleOwner) { poll ->
            if (poll == null) return@observe
            bindPoll(poll)
        }
        viewModel.fullImages.observe(viewLifecycleOwner) { images ->
            adapter?.fullImages = images
        }
        // Opened a link to a poll I already answered, own, or that is over.
        viewModel.showResultsInstead.observe(viewLifecycleOwner) { show ->
            if (show != true) return@observe
            navigateSafely(R.id.action_vote_to_results, pollArgs(pollId))
        }
        // Opened a link to a poll shared by someone who is not a friend.
        viewModel.notAllowed.observe(viewLifecycleOwner) { blocked ->
            if (blocked != true) return@observe
            showMessage(R.string.poll_not_visible)
            navigateSafely(R.id.action_global_feed)
        }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progress.isVisible = loading
            binding.sendVoteButton.isEnabled = !loading
        }
        observeMessage(viewModel.error, viewModel::errorShown)
        viewModel.voteSent.observe(viewLifecycleOwner) { sent ->
            if (sent != true) return@observe
            showMessage(R.string.vote_saved)
            navigateSafely(R.id.action_vote_to_results, pollArgs(pollId))
        }
    }

    private fun bindPoll(poll: Poll) {
        binding.question.text = poll.question
        binding.subtitle.text = getString(
            if (poll.mode == Poll.MODE_RATING) R.string.rate_hint else R.string.error_pick_one
        )
        binding.timeLeft.text = TimeFormat.timeLeft(requireContext(), poll.millisLeft)

        if (adapter == null) {
            adapter = VoteImagesAdapter(
                mode = poll.mode,
                onSelect = { image -> viewModel.selectedImageId = image.id },
                onRate = { image, stars -> viewModel.ratings[image.id] = stars },
            )
            binding.imagesList.adapter = adapter
            adapter?.fullImages = viewModel.fullImages.value.orEmpty()
        }
        adapter?.submitList(poll.images)
    }

    override fun onDestroyView() {
        binding.imagesList.adapter = null
        adapter = null
        super.onDestroyView()
    }
}

package com.hila.snapvote.ui.results

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hila.snapvote.R
import com.hila.snapvote.data.model.Comment
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.databinding.FragmentResultsBinding
import com.hila.snapvote.ui.common.BaseFragment
import com.hila.snapvote.ui.common.pollArgs
import com.hila.snapvote.ui.common.pollIdArgument
import com.hila.snapvote.util.TimeFormat
import com.hila.snapvote.util.loadBase64

/** Winner, percentages per image, comments and the automatic-deletion status. */
class ResultsFragment : BaseFragment<FragmentResultsBinding>(FragmentResultsBinding::inflate) {

    private val viewModel: ResultsViewModel by viewModels()
    private val pollId: String by lazy { pollIdArgument() }

    private val adapter = ResultsAdapter()
    private val commentAdapter by lazy {
        CommentAdapter(
            currentUid = viewModel.currentUid,
            pollOwnerId = { viewModel.poll.value?.ownerId },
            onDelete = ::confirmDeleteComment,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.resultsList.adapter = adapter
        binding.commentsList.adapter = commentAdapter
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.deleteButton.setOnClickListener { confirmDelete() }
        binding.sendCommentButton.setOnClickListener { sendComment() }

        viewModel.observe(pollId)

        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            commentAdapter.submitList(comments)
            binding.commentsEmpty.isVisible = comments.isEmpty()
        }
        viewModel.poll.observe(viewLifecycleOwner) { poll ->
            if (poll == null) return@observe
            bindPoll(poll)
        }
        // The winner picture is fetched separately – redraw once it lands.
        viewModel.fullImages.observe(viewLifecycleOwner) {
            viewModel.poll.value?.let(::bindPoll)
        }
        viewModel.loading.observe(viewLifecycleOwner) { binding.progress.isVisible = it }
        observeMessage(viewModel.error, viewModel::errorShown)
        viewModel.deleted.observe(viewLifecycleOwner) { deleted ->
            if (deleted == true) findNavController().popBackStack()
        }
    }

    private fun bindPoll(poll: Poll) {
        val isMine = poll.ownerId == viewModel.currentUid
        binding.question.text = poll.question
        binding.deleteButton.isVisible = isMine
        binding.shareButton.setOnClickListener { sharePoll(poll) }

        // Only the person who created the poll gets to see who voted for what.
        binding.votersButton.isVisible = isMine && poll.voteCount > 0
        binding.votersButton.setOnClickListener {
            navigateSafely(R.id.action_results_to_voters, pollArgs(poll.id))
        }

        val winner = poll.winner()
        binding.winnerCard.isVisible = winner != null
        if (winner != null) {
            // The winner is the one picture shown full size, so it is also the one that
            // matters most to stop showing the moment the poll is over.
            val full = viewModel.fullImages.value?.get(winner.id)
            binding.winnerImage.loadBase64(
                (full ?: winner.thumb).takeIf { poll.showsImagesTo(viewModel.currentUid) },
                cacheKey = "${poll.id}/${winner.id}/" + if (full != null) "full" else "thumb",
            )
            binding.winnerCaption.text = getString(
                R.string.image_percent, winner.label, poll.percentOf(winner.id)
            )
        }

        binding.summary.text = buildString {
            append(getString(R.string.votes_count, poll.voteCount))
            append(" · ")
            append(
                if (poll.isClosed) getString(R.string.poll_closed)
                else TimeFormat.timeLeft(requireContext(), poll.millisLeft)
            )
        }

        // Three different truths, depending on who is reading it.
        binding.deletionNotice.text = getString(
            when {
                !poll.isClosed -> R.string.images_will_be_deleted
                isMine -> R.string.images_kept_for_owner
                else -> R.string.images_deleted
            }
        )

        adapter.viewerUid = viewModel.currentUid
        adapter.poll = poll
        adapter.submitList(poll.images.sortedByDescending { poll.tally[it.id] ?: 0 })
    }

    private fun sharePoll(poll: Poll) {
        // A real https link, so WhatsApp turns it into something tappable.
        val link = getString(R.string.poll_link, poll.id)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, poll.question, link))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_poll)))
    }

    private fun sendComment() {
        val text = binding.commentInput.text?.toString().orEmpty().trim()
        if (text.isEmpty()) return
        viewModel.addComment(pollId, text)
        binding.commentInput.setText("")
    }

    private fun confirmDeleteComment(comment: Comment) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.delete_comment_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteComment(pollId, comment) }
            .show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_poll)
            .setMessage(R.string.delete_poll_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deletePoll(requireContext()) }
            .show()
    }

    override fun onDestroyView() {
        binding.resultsList.adapter = null
        binding.commentsList.adapter = null
        super.onDestroyView()
    }
}

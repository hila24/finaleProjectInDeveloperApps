package com.hila.snapvote.ui.feed

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hila.snapvote.R
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.databinding.ItemPollBinding
import com.hila.snapvote.ui.common.RefreshableListAdapter
import com.hila.snapvote.util.TimeFormat
import com.hila.snapvote.util.loadBase64

/** Renders the poll cards in the feed and in "הסקרים שלי". */
class PollAdapter(
    private val onClick: (Poll) -> Unit,
    private val onLongClick: ((Poll) -> Unit)? = null,
) : RefreshableListAdapter<Poll, PollAdapter.PollViewHolder>(DIFF) {

    var votedPollIds: Set<String> = emptySet()
        set(value) {
            field = value
            refreshRows()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollViewHolder {
        val binding = ItemPollBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PollViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PollViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PollViewHolder(private val binding: ItemPollBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(poll: Poll) {
            val context = binding.root.context
            binding.question.text = poll.question
            binding.owner.text = context.getString(R.string.by_owner, poll.ownerName)
            binding.timeLeft.text = TimeFormat.timeLeft(context, poll.millisLeft)
            binding.voteCount.text = context.getString(R.string.votes_count, poll.voteCount)
            binding.votedBadge.isVisible = poll.id in votedPollIds

            val slots = listOf(binding.image1, binding.image2, binding.image3)
            slots.forEachIndexed { index, view -> view.bindImage(poll, index) }

            binding.root.setOnClickListener { onClick(poll) }
            binding.root.setOnLongClickListener {
                onLongClick?.invoke(poll)
                onLongClick != null
            }
        }

        private fun ImageView.bindImage(poll: Poll, index: Int) {
            val image = poll.images.getOrNull(index)
            if (image == null) {
                isVisible = false
                return
            }
            isVisible = true
            // A finished poll keeps its slot and its label, but not its picture.
            loadBase64(
                image.thumb.takeIf { poll.showsImages },
                cacheKey = "${poll.id}/${image.id}/thumb",
            )
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Poll>() {
            override fun areItemsTheSame(oldItem: Poll, newItem: Poll) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Poll, newItem: Poll) = oldItem == newItem
        }
    }
}

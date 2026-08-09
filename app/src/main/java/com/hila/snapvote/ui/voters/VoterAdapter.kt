package com.hila.snapvote.ui.voters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hila.snapvote.R
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.data.model.Vote
import com.hila.snapvote.databinding.ItemVoterBinding
import com.hila.snapvote.ui.common.RefreshableListAdapter

class VoterAdapter : RefreshableListAdapter<Vote, VoterAdapter.VoterViewHolder>(DIFF) {

    /** Needed to turn image ids into the labels the voter actually saw ("תמונה A"). */
    var poll: Poll? = null
        set(value) {
            field = value
            refreshRows()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoterViewHolder {
        val binding = ItemVoterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VoterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VoterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VoterViewHolder(private val binding: ItemVoterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(vote: Vote) {
            val context = binding.root.context
            val name = vote.userName.ifEmpty { "משתמש" }
            binding.username.text = name
            binding.avatar.text = name.take(1).uppercase()

            val poll = poll
            binding.choice.text = when {
                vote.ratings.isNotEmpty() && poll != null -> {
                    val summary = poll.images.joinToString("  ") { image ->
                        "${image.label}: ${vote.ratings[image.id] ?: 0}★"
                    }
                    context.getString(R.string.voter_rated, summary)
                }
                vote.choiceImageId.isNotEmpty() -> {
                    val label = poll?.images
                        ?.firstOrNull { it.id == vote.choiceImageId }?.label
                        ?: vote.choiceImageId
                    context.getString(R.string.voter_chose, label)
                }
                else -> ""
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Vote>() {
            override fun areItemsTheSame(oldItem: Vote, newItem: Vote) =
                oldItem.userId == newItem.userId

            override fun areContentsTheSame(oldItem: Vote, newItem: Vote) = oldItem == newItem
        }
    }
}

package com.hila.snapvote.ui.results

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hila.snapvote.R
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.data.model.PollImage
import com.hila.snapvote.databinding.ItemResultBinding
import com.hila.snapvote.ui.common.RefreshableListAdapter
import com.hila.snapvote.util.loadBase64
import java.util.Locale

/** One row per image: thumbnail, percentage bar and the raw numbers. */
class ResultsAdapter : RefreshableListAdapter<PollImage, ResultsAdapter.ResultViewHolder>(DIFF) {

    var poll: Poll? = null
        set(value) {
            field = value
            refreshRows()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ItemResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ResultViewHolder(private val binding: ItemResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(image: PollImage) {
            val poll = poll ?: return
            val context = binding.root.context
            val percent = poll.percentOf(image.id)

            binding.image.loadBase64(image.thumb, cacheKey = "${poll.id}/${image.id}/thumb")

            binding.title.text = context.getString(R.string.image_percent, image.label, percent)
            binding.bar.setProgressCompat(percent, true)

            binding.subtitle.text = if (poll.mode == Poll.MODE_RATING) {
                context.getString(
                    R.string.avg_rating,
                    String.format(Locale.getDefault(), "%.1f", poll.averageOf(image.id))
                )
            } else {
                context.getString(R.string.votes_count, poll.tally[image.id] ?: 0)
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<PollImage>() {
            override fun areItemsTheSame(oldItem: PollImage, newItem: PollImage) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PollImage, newItem: PollImage) =
                oldItem == newItem
        }
    }
}

package com.hila.snapvote.ui.vote

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hila.snapvote.R
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.data.model.PollImage
import com.hila.snapvote.databinding.ItemVoteImageBinding
import com.hila.snapvote.ui.common.RefreshableListAdapter
import com.hila.snapvote.util.loadBase64

/**
 * One card per image. In [Poll.MODE_SINGLE] tapping the card selects it,
 * in [Poll.MODE_RATING] the five stars under the image are used instead.
 */
class VoteImagesAdapter(
    private val mode: String,
    private val onSelect: (PollImage) -> Unit,
    private val onRate: (PollImage, Int) -> Unit,
) : RefreshableListAdapter<PollImage, VoteImagesAdapter.ImageViewHolder>(DIFF) {

    private var selectedId: String? = null
    private val ratings = mutableMapOf<String, Int>()

    /** imageId -> full-size Base64 JPEG, filled in once the documents are fetched. */
    var fullImages: Map<String, String> = emptyMap()
        set(value) {
            field = value
            refreshRows()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemVoteImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ImageViewHolder(private val binding: ItemVoteImageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val stars: List<ImageView> = listOf(
            binding.star1, binding.star2, binding.star3, binding.star4, binding.star5
        )

        fun bind(image: PollImage) {
            val context = binding.root.context
            // Show the thumbnail immediately, swap in the full picture once it arrives.
            binding.image.loadBase64(
                fullImages[image.id] ?: image.thumb,
                cacheKey = image.id + if (fullImages.containsKey(image.id)) "/full" else "/thumb",
            )
            binding.label.text = context.getString(R.string.image_label, image.label)

            if (mode == Poll.MODE_SINGLE) {
                binding.starsRow.isVisible = false
                binding.card.isChecked = image.id == selectedId
                binding.card.setOnClickListener {
                    // Redraw only the two cards whose checked state actually changed.
                    val previous = currentList.indexOfFirst { it.id == selectedId }
                    selectedId = image.id
                    onSelect(image)
                    if (previous >= 0) notifyItemChanged(previous)
                    notifyItemChanged(bindingAdapterPosition)
                }
            } else {
                binding.starsRow.isVisible = true
                binding.card.isCheckable = false
                binding.card.setOnClickListener(null)
                paintStars(ratings[image.id] ?: 0)
                stars.forEachIndexed { index, star ->
                    star.setOnClickListener {
                        val value = index + 1
                        ratings[image.id] = value
                        paintStars(value)
                        onRate(image, value)
                    }
                }
            }
        }

        private fun paintStars(value: Int) {
            stars.forEachIndexed { index, star ->
                star.setImageResource(
                    if (index < value) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                )
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

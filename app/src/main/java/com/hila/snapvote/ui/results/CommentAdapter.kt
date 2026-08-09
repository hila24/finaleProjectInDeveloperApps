package com.hila.snapvote.ui.results

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hila.snapvote.data.model.Comment
import com.hila.snapvote.databinding.ItemCommentBinding

/** Comments under a poll. The delete button shows on your own comments only. */
class CommentAdapter(
    private val currentUid: String?,
    private val pollOwnerId: () -> String?,
    private val onDelete: (Comment) -> Unit,
) : ListAdapter<Comment, CommentAdapter.CommentViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(private val binding: ItemCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            val name = comment.userName.ifEmpty { "משתמש" }
            binding.username.text = name
            binding.avatar.text = name.take(1).uppercase()
            binding.text.text = comment.text

            // Mine to delete, or anything under a poll I own.
            val canDelete = comment.userId == currentUid || pollOwnerId() == currentUid
            binding.deleteButton.isVisible = canDelete
            binding.deleteButton.setOnClickListener { onDelete(comment) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Comment>() {
            override fun areItemsTheSame(oldItem: Comment, newItem: Comment) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Comment, newItem: Comment) = oldItem == newItem
        }
    }
}

package com.hila.snapvote.ui.friends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hila.snapvote.data.model.Friend
import com.hila.snapvote.databinding.ItemFriendBinding

class FriendAdapter(
    private val onRemove: (Friend) -> Unit,
) : ListAdapter<Friend, FriendAdapter.FriendViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val binding = ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FriendViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FriendViewHolder(private val binding: ItemFriendBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(friend: Friend) {
            binding.username.text = friend.username
            binding.avatar.text = friend.username.take(1).uppercase()
            binding.removeButton.setOnClickListener { onRemove(friend) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Friend>() {
            override fun areItemsTheSame(oldItem: Friend, newItem: Friend) =
                oldItem.uid == newItem.uid

            override fun areContentsTheSame(oldItem: Friend, newItem: Friend) = oldItem == newItem
        }
    }
}

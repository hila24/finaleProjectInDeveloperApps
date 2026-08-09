package com.hila.snapvote.ui.friends

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hila.snapvote.R
import com.hila.snapvote.data.model.Friend
import com.hila.snapvote.databinding.FragmentFriendsBinding
import com.hila.snapvote.ui.common.BaseFragment

/** Find people by username, add them, and manage the list. */
class FriendsFragment : BaseFragment<FragmentFriendsBinding>(FragmentFriendsBinding::inflate) {

    private val viewModel: FriendsViewModel by viewModels()
    private val adapter by lazy { FriendAdapter(onRemove = ::confirmRemove) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.friendsList.adapter = adapter
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.searchButton.setOnClickListener { search() }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search()
                true
            } else {
                false
            }
        }

        viewModel.friendsList.observe(viewLifecycleOwner) { friends ->
            adapter.submitList(friends)
            binding.emptyState.isVisible = friends.isEmpty()
        }
        viewModel.searchResult.observe(viewLifecycleOwner) { user ->
            binding.resultCard.isVisible = user != null
            if (user == null) return@observe
            binding.resultName.text = user.username
            binding.resultAvatar.text = user.username.take(1).uppercase()

            val alreadyFriend = viewModel.friendsList.value.orEmpty().any { it.uid == user.uid }
            val isMe = user.uid == viewModel.currentUid
            binding.addButton.isEnabled = !alreadyFriend && !isMe
            binding.addButton.setOnClickListener { viewModel.addFriend(user) }
        }
        viewModel.loading.observe(viewLifecycleOwner) { binding.progress.isVisible = it }
        observeMessage(viewModel.message, viewModel::messageShown)
    }

    private fun search() {
        viewModel.clearSearch()
        viewModel.search(binding.searchInput.text?.toString().orEmpty())
    }

    private fun confirmRemove(friend: Friend) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remove_friend)
            .setMessage(getString(R.string.remove_friend_confirm, friend.username))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ -> viewModel.removeFriend(friend) }
            .show()
    }

    override fun onDestroyView() {
        binding.friendsList.adapter = null
        super.onDestroyView()
    }
}

package com.hila.snapvote.ui.friends

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hila.snapvote.data.model.Friend
import com.hila.snapvote.data.model.User
import com.hila.snapvote.data.repository.AuthRepository
import com.hila.snapvote.data.repository.CannotAddSelfException
import com.hila.snapvote.data.repository.FriendRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class FriendsViewModel(
    private val friends: FriendRepository = FriendRepository(),
    private val auth: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _friends = MutableLiveData<List<Friend>>(emptyList())
    val friendsList: LiveData<List<Friend>> = _friends

    /** The person found by the last search, or null when there was no match. */
    private val _searchResult = MutableLiveData<User?>()
    val searchResult: LiveData<User?> = _searchResult

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    val currentUid: String? get() = auth.currentUid

    init {
        val uid = auth.currentUid
        if (uid != null) {
            viewModelScope.launch {
                friends.friendsFlow(uid)
                    .catch { _message.value = it.message }
                    .collect { _friends.value = it }
            }
        }
    }

    fun search(username: String) {
        val name = username.trim()
        if (name.isEmpty()) return
        _loading.value = true
        viewModelScope.launch {
            runCatching { friends.findByUsername(name) }
                .onSuccess { user ->
                    _searchResult.value = user
                    if (user == null) _message.value = "לא נמצא משתמש בשם \"$name\""
                    else if (user.uid == auth.currentUid) _message.value = "זו את 🙂"
                    else if (_friends.value.orEmpty().any { it.uid == user.uid }) {
                        _message.value = "${user.username} כבר ברשימת החברים שלך"
                    }
                }
                .onFailure { _message.value = it.message ?: "החיפוש נכשל" }
            _loading.value = false
        }
    }

    fun addFriend(user: User) {
        val uid = auth.currentUid ?: return
        val myName = auth.currentName.ifEmpty { "משתמש" }
        _loading.value = true
        viewModelScope.launch {
            runCatching { friends.addFriend(uid, myName, user) }
                .onSuccess {
                    _message.value = "${user.username} נוספה לרשימת החברים 🎉"
                    _searchResult.value = null
                }
                .onFailure {
                    _message.value = when (it) {
                        is CannotAddSelfException -> "אי אפשר להוסיף את עצמך"
                        else -> it.message ?: "ההוספה נכשלה"
                    }
                }
            _loading.value = false
        }
    }

    fun removeFriend(friend: Friend) {
        val uid = auth.currentUid ?: return
        viewModelScope.launch {
            runCatching { friends.removeFriend(uid, friend.uid) }
                .onSuccess { _message.value = "${friend.username} הוסרה" }
                .onFailure { _message.value = it.message ?: "ההסרה נכשלה" }
        }
    }

    fun clearSearch() {
        _searchResult.value = null
    }

    fun messageShown() {
        _message.value = null
    }
}

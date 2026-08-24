package com.hila.snapvote.ui.profile

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.data.model.User
import com.hila.snapvote.data.repository.AuthRepository
import com.hila.snapvote.data.repository.FriendRepository
import com.hila.snapvote.data.repository.PollRepository
import com.hila.snapvote.util.PollNotifications
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val auth: AuthRepository = AuthRepository(),
    private val polls: PollRepository = PollRepository(),
    private val friends: FriendRepository = FriendRepository(),
) : ViewModel() {

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _friendsCount = MutableLiveData(0)
    val friendsCount: LiveData<Int> = _friendsCount

    private val _myPolls = MutableLiveData<List<Poll>>()
    val myPolls: LiveData<List<Poll>> = _myPolls

    /** Finished polls of friends that I could see – reachable nowhere else once closed. */
    private val _archivedPolls = MutableLiveData<List<Poll>>()
    val archivedPolls: LiveData<List<Poll>> = _archivedPolls

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    val currentUid: String? get() = auth.currentUid

    init {
        viewModelScope.launch {
            auth.currentUserFlow()
                .catch { _error.value = it.message }
                .collect { _user.value = it }
        }
        val uid = auth.currentUid
        if (uid != null) {
            viewModelScope.launch {
                polls.myPollsFlow(uid)
                    .catch { _error.value = it.message }
                    .collect { _myPolls.value = it }
            }
            viewModelScope.launch {
                polls.archivedPollsFlow(uid)
                    .catch { _error.value = it.message }
                    .collect { _archivedPolls.value = it }
            }
            viewModelScope.launch {
                friends.friendsFlow(uid)
                    .catch { _error.value = it.message }
                    .collect { _friendsCount.value = it.size }
            }
        }
    }

    /**
     * Signing out also drops the pending reminders. They are scheduled per device, so
     * leaving them behind would notify whoever signs in next about polls that are not
     * theirs – easy to hit when two accounts are tested on the same phone.
     */
    fun logout(context: Context) {
        PollNotifications.cancelAll(context)
        auth.logout()
    }

    fun errorShown() {
        _error.value = null
    }
}

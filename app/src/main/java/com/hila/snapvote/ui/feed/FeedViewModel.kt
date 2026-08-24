package com.hila.snapvote.ui.feed

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.data.repository.AuthRepository
import com.hila.snapvote.data.repository.PollRepository
import com.hila.snapvote.util.PollNotifications
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class FeedViewModel(
    private val polls: PollRepository = PollRepository(),
    private val auth: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _activePolls = MutableLiveData<List<Poll>>()
    val activePolls: LiveData<List<Poll>> = _activePolls

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /** Poll ids the signed-in user already voted in – drives the "כבר הצבעת" badge. */
    private val _votedPollIds = MutableLiveData<Set<String>>(emptySet())
    val votedPollIds: LiveData<Set<String>> = _votedPollIds

    val currentUid: String? get() = auth.currentUid

    /**
     * Polls already handed to WorkManager in this session. The feed is a live snapshot
     * listener that fires on every vote anyone casts, so without this the same reminders
     * would be re-enqueued over and over.
     */
    private val scheduled = mutableSetOf<String>()

    /** The notification permission is worth asking for once, not on every feed update. */
    var askedForNotifications = false

    init {
        observePolls()
    }

    private fun observePolls() {
        val uid = auth.currentUid
        if (uid == null) {
            _loading.value = false
            return
        }
        viewModelScope.launch {
            polls.activePollsFlow(uid)
                .catch { throwable ->
                    _loading.value = false
                    _error.value = throwable.message ?: "שגיאה בטעינת הסקרים"
                }
                .collect { list ->
                    // The feed is "הסקרים של החברים" – my own polls live in the profile.
                    val friendsPolls = list.filterNot { it.ownerId == uid }
                    _activePolls.value = friendsPolls
                    _loading.value = false
                    refreshVotedState(friendsPolls)
                }
        }
    }

    private fun refreshVotedState(list: List<Poll>) {
        val uid = auth.currentUid ?: return
        viewModelScope.launch {
            _votedPollIds.value = list
                .filter { poll -> runCatching { polls.myVote(poll.id, uid) != null }.getOrDefault(false) }
                .map { it.id }
                .toSet()
        }
    }

    /**
     * Books deadline reminders for friends' polls on this phone.
     *
     * WorkManager only schedules work on the device it runs on, so the author's phone
     * cannot remind anybody else. Instead every device books its own reminders for the
     * polls it can see – which is why a friend has to open the app once while a poll is
     * running before they can be reminded about it.
     */
    fun scheduleReminders(context: Context) {
        val list = _activePolls.value ?: return
        val voted = _votedPollIds.value.orEmpty()
        for (poll in list) {
            val deadline = poll.deadline?.toDate() ?: continue
            if (poll.isClosed || !scheduled.add(poll.id)) continue
            PollNotifications.scheduleFor(
                context = context,
                pollId = poll.id,
                question = poll.question,
                deadline = deadline,
                withReminder = poll.id !in voted,
            )
        }
    }

    fun errorShown() {
        _error.value = null
    }
}

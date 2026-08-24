package com.hila.snapvote.ui.vote

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.data.repository.AlreadyVotedException
import com.hila.snapvote.data.repository.AuthRepository
import com.hila.snapvote.data.repository.PollClosedException
import com.hila.snapvote.data.repository.PollRepository
import com.hila.snapvote.util.PollNotifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VoteViewModel(
    private val polls: PollRepository = PollRepository(),
    private val auth: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _poll = MutableLiveData<Poll?>()
    val poll: LiveData<Poll?> = _poll

    /** imageId -> full-size Base64 JPEG. */
    private val _fullImages = MutableLiveData<Map<String, String>>(emptyMap())
    val fullImages: LiveData<Map<String, String>> = _fullImages

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _voteSent = MutableLiveData(false)
    val voteSent: LiveData<Boolean> = _voteSent

    /**
     * True when there is nothing to vote on: the poll is mine, already closed, or I
     * voted in it before. Happens when a shared link is opened after the fact.
     */
    private val _showResultsInstead = MutableLiveData(false)
    val showResultsInstead: LiveData<Boolean> = _showResultsInstead

    /** Set when the poll cannot be read at all – e.g. we are not on the friends list. */
    private val _notAllowed = MutableLiveData(false)
    val notAllowed: LiveData<Boolean> = _notAllowed

    /** Single mode: the chosen image. */
    var selectedImageId: String? = null

    /** Rating mode: imageId -> stars. */
    val ratings = mutableMapOf<String, Int>()

    fun load(pollId: String) {
        val uid = auth.currentUid
        viewModelScope.launch {
            runCatching { polls.pollFlow(pollId).first() }
                .onSuccess { poll ->
                    if (poll == null) {
                        _notAllowed.value = true
                        return@onSuccess
                    }
                    val votedBefore = uid != null &&
                        runCatching { polls.myVote(pollId, uid) != null }.getOrDefault(false)
                    if (poll.isClosed || poll.ownerId == uid || votedBefore) {
                        _showResultsInstead.value = true
                    } else {
                        _poll.value = poll
                    }
                }
                .onFailure { _notAllowed.value = true }
        }
        viewModelScope.launch {
            runCatching { polls.fullImages(pollId) }
                .onSuccess { _fullImages.value = it }
        }
    }

    fun submit(context: Context, pollId: String) {
        val uid = auth.currentUid ?: return
        val poll = _poll.value ?: return

        if (poll.mode == Poll.MODE_SINGLE && selectedImageId == null) {
            _error.value = "יש לבחור תמונה אחת"
            return
        }
        if (poll.mode == Poll.MODE_RATING && ratings.size < poll.images.size) {
            _error.value = "יש לדרג את כל התמונות"
            return
        }

        _loading.value = true
        viewModelScope.launch {
            runCatching {
                polls.castVote(
                    pollId = pollId,
                    uid = uid,
                    userName = auth.currentName.ifEmpty { "משתמש" },
                    choiceImageId = selectedImageId.takeIf { poll.mode == Poll.MODE_SINGLE },
                    ratings = if (poll.mode == Poll.MODE_RATING) ratings.toMap() else emptyMap(),
                )
            }.onSuccess {
                // Nothing left to hurry for; the closing notification still stands.
                PollNotifications.cancelReminderFor(context, pollId)
                _voteSent.value = true
            }.onFailure { throwable ->
                _error.value = when (throwable) {
                    is AlreadyVotedException -> "כבר הצבעת בסקר הזה"
                    is PollClosedException -> "הסקר כבר נסגר"
                    else -> throwable.message ?: "ההצבעה לא נשלחה"
                }
            }
            _loading.value = false
        }
    }

    fun errorShown() {
        _error.value = null
    }
}

package com.hila.snapvote.ui.results

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hila.snapvote.data.model.Comment
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.data.repository.AuthRepository
import com.hila.snapvote.data.repository.PollRepository
import com.hila.snapvote.util.PollNotifications
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Live results – the numbers move as other people vote. */
class ResultsViewModel(
    private val polls: PollRepository = PollRepository(),
    private val auth: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _poll = MutableLiveData<Poll?>()
    val poll: LiveData<Poll?> = _poll

    /** imageId -> full-size Base64 JPEG, used for the big winner picture. */
    private val _fullImages = MutableLiveData<Map<String, String>>(emptyMap())
    val fullImages: LiveData<Map<String, String>> = _fullImages

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _deleted = MutableLiveData(false)
    val deleted: LiveData<Boolean> = _deleted

    private val _comments = MutableLiveData<List<Comment>>(emptyList())
    val comments: LiveData<List<Comment>> = _comments

    val currentUid: String? get() = auth.currentUid

    fun observe(pollId: String) {
        viewModelScope.launch {
            runCatching { polls.fullImages(pollId) }
                .onSuccess { _fullImages.value = it }
        }
        viewModelScope.launch {
            polls.commentsFlow(pollId)
                .catch { _error.value = it.message }
                .collect { _comments.value = it }
        }
        viewModelScope.launch {
            polls.pollFlow(pollId)
                .catch { throwable ->
                    _loading.value = false
                    _error.value = throwable.message ?: "שגיאה בטעינת התוצאות"
                }
                .collect { poll ->
                    _poll.value = poll
                    _loading.value = false
                }
        }
    }

    fun deletePoll(context: Context) {
        val poll = _poll.value ?: return
        _loading.value = true
        viewModelScope.launch {
            runCatching { polls.deletePoll(poll) }
                .onSuccess {
                    PollNotifications.cancelFor(context, poll.id)
                    _deleted.value = true
                }
                .onFailure { _error.value = it.message ?: "לא הצלחנו למחוק את הסקר" }
            _loading.value = false
        }
    }

    fun addComment(pollId: String, text: String) {
        val uid = auth.currentUid ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching {
                polls.addComment(pollId, uid, auth.currentName.ifEmpty { "משתמש" }, text)
            }.onFailure { _error.value = it.message ?: "התגובה לא נשלחה" }
        }
    }

    fun deleteComment(pollId: String, comment: Comment) {
        viewModelScope.launch {
            runCatching { polls.deleteComment(pollId, comment.id) }
                .onFailure { _error.value = it.message ?: "לא הצלחנו למחוק את התגובה" }
        }
    }

    fun errorShown() {
        _error.value = null
    }
}

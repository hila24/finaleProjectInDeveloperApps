package com.hila.snapvote.ui.voters

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.data.model.Vote
import com.hila.snapvote.data.repository.PollRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Who voted in one poll, and what each of them picked. Owner-only screen. */
class VotersViewModel(
    private val polls: PollRepository = PollRepository(),
) : ViewModel() {

    private val _poll = MutableLiveData<Poll?>()
    val poll: LiveData<Poll?> = _poll

    private val _votes = MutableLiveData<List<Vote>>()
    val votes: LiveData<List<Vote>> = _votes

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun load(pollId: String) {
        viewModelScope.launch {
            runCatching { polls.pollFlow(pollId).first() }
                .onSuccess { _poll.value = it }
                .onFailure { _error.value = it.message }
        }
        viewModelScope.launch {
            polls.votesFlow(pollId)
                .catch {
                    _loading.value = false
                    _error.value = it.message ?: "לא הצלחנו לטעון את ההצבעות"
                }
                .collect {
                    _votes.value = it
                    _loading.value = false
                }
        }
    }

    fun errorShown() {
        _error.value = null
    }
}

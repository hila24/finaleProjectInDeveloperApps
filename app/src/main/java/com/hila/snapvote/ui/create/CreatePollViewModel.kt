package com.hila.snapvote.ui.create

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.data.repository.AuthRepository
import com.hila.snapvote.data.repository.FriendRepository
import com.hila.snapvote.data.repository.PollRepository
import com.hila.snapvote.util.PollNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

class CreatePollViewModel(
    private val polls: PollRepository = PollRepository(),
    private val auth: AuthRepository = AuthRepository(),
    private val friends: FriendRepository = FriendRepository(),
) : ViewModel() {

    private val _images = MutableLiveData<List<Uri>>(emptyList())
    val images: LiveData<List<Uri>> = _images

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _uploadStatus = MutableLiveData<String?>()
    val uploadStatus: LiveData<String?> = _uploadStatus

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /** Id of the freshly created poll – the fragment navigates to its results. */
    private val _createdPollId = MutableLiveData<String?>()
    val createdPollId: LiveData<String?> = _createdPollId

    var mode: String = Poll.MODE_SINGLE
    var deadlineHours: Int = 24

    /**
     * Copies the picked images into the app cache right away, so the poll can still be
     * published after the gallery permission grant expires.
     */
    fun addImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> runCatching { copyToCache(context, uri) }.getOrNull() }
            }
            val room = MAX_IMAGES - (_images.value?.size ?: 0)
            if (room <= 0) {
                _error.value = "אפשר להעלות עד $MAX_IMAGES תמונות בסקר"
                return@launch
            }
            _images.value = _images.value.orEmpty() + copied.take(room)
        }
    }

    fun removeImage(uri: Uri) {
        _images.value = _images.value.orEmpty().filterNot { it == uri }
    }

    fun publish(context: Context, question: String) {
        val uid = auth.currentUid
        if (uid == null) {
            _error.value = "צריך להתחבר מחדש"
            return
        }
        val pickedImages = _images.value.orEmpty()
        val deadline = Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(deadlineHours.toLong()))
        _loading.value = true
        viewModelScope.launch {
            runCatching {
                // Snapshot of the friends list – it becomes the poll's audience.
                val friendIds = friends.friendIds(uid)
                polls.createPoll(
                    context = context,
                    question = question,
                    mode = mode,
                    deadline = deadline,
                    imageUris = pickedImages,
                    ownerId = uid,
                    ownerName = auth.currentName.ifEmpty { "משתמש" },
                    friendIds = friendIds,
                    onProgress = { done, total ->
                        _uploadStatus.postValue("מעלה תמונה $done מתוך $total…")
                    },
                )
            }.onSuccess { pollId ->
                _uploadStatus.value = null
                // Remind me an hour before it closes, and once it has.
                PollNotifications.scheduleFor(context, pollId, question, deadline)
                _createdPollId.value = pollId
            }.onFailure {
                _uploadStatus.value = null
                _error.value = it.message ?: "לא הצלחנו לפרסם את הסקר"
            }
            _loading.value = false
        }
    }

    fun errorShown() {
        _error.value = null
    }

    private fun copyToCache(context: Context, uri: Uri): Uri {
        val dir = File(context.cacheDir, "picked").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read the picked image")
        return Uri.fromFile(target)
    }

    companion object {
        const val MAX_IMAGES = 6
    }
}

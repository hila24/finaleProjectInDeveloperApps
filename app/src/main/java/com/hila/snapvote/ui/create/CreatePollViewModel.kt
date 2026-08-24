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

    /** One of the preset chips, counted from the moment the poll is published. */
    var deadlineHours: Int = 24

    /**
     * An exact moment the user picked themselves. When it is set it wins over
     * [deadlineHours]; picking a preset chip again clears it.
     *
     * It lives here rather than in the fragment so it survives a screen rotation.
     */
    private val _customDeadline = MutableLiveData<Date?>(null)
    val customDeadline: LiveData<Date?> = _customDeadline

    fun setCustomDeadline(at: Date?) {
        _customDeadline.value = at
    }

    /**
     * The day chosen in the date dialog, waiting for the clock dialog that follows it.
     * Kept here so that rotating the phone between the two dialogs does not lose it.
     */
    var pendingDateUtc: Long? = null

    /**
     * The deadline to publish with. Presets are resolved now, at publish time, so
     * "שעה" means an hour from publishing and not an hour from picking the chip.
     */
    private fun resolveDeadline(): Date =
        _customDeadline.value
            ?: Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(deadlineHours.toLong()))

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
        val deadline = resolveDeadline()
        // A hand-picked time can go stale while the images upload, and a poll that is
        // already closed would be rejected by the security rules anyway.
        if (deadline.time <= System.currentTimeMillis()) {
            _error.value = "המועד שנבחר כבר עבר – בחרי מועד חדש"
            return
        }
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
                // Only the closing notification: the author cannot vote in their own
                // poll, so "נותרו 5 דקות להצבעה" would be telling them to do something
                // the app will not let them do. Their friends' phones book that one.
                // ownerId makes the closing job delete the pictures at the deadline,
                // rather than waiting for the next time this app is opened.
                PollNotifications.scheduleFor(
                    context, pollId, question, deadline,
                    withReminder = false, ownerId = uid,
                )
                // The pictures are safely in Firestore now, so the working copies on
                // this phone have done their job.
                clearWorkingCopies(context)
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

    /**
     * Deletes the app's working copies of the picked pictures.
     *
     * Picking an image copies it into the cache (the gallery's permission grant expires
     * before the upload finishes) and the camera writes a file of its own. Nothing used
     * to remove either, so the phone that created a poll kept a full-size copy of every
     * picture it ever uploaded – long after the poll itself was deleted. That quietly
     * contradicts the one thing the app promises.
     *
     * Safe to call whenever no upload is in flight: the pictures are already in
     * Firestore by then, and both folders are recreated on the next pick.
     */
    private suspend fun clearWorkingCopies(context: Context) = withContext(Dispatchers.IO) {
        listOf(DIR_PICKED, DIR_CAMERA).forEach { name ->
            runCatching { File(context.cacheDir, name).deleteRecursively() }
        }
    }

    /**
     * Sweeps copies left behind by a poll that was started and then abandoned.
     * Called once when the screen opens, before anything has been picked.
     */
    fun clearAbandonedCopies(context: Context) {
        viewModelScope.launch { clearWorkingCopies(context) }
    }

    /** Guards the one-time sweep of leftovers, so a rotation does not delete live picks. */
    var clearedStaleCopies = false

    private fun copyToCache(context: Context, uri: Uri): Uri {
        val dir = File(context.cacheDir, DIR_PICKED).apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read the picked image")
        return Uri.fromFile(target)
    }

    companion object {
        const val MAX_IMAGES = 6

        /** Cache folders holding the app's working copies of the picked pictures. */
        const val DIR_PICKED = "picked"
        const val DIR_CAMERA = "camera"
    }
}

package com.hila.snapvote.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.hila.snapvote.data.model.Comment
import com.hila.snapvote.data.model.Poll
import com.hila.snapvote.data.model.PollImage
import com.hila.snapvote.data.model.PollImageData
import com.hila.snapvote.data.model.Vote
import com.hila.snapvote.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

class AlreadyVotedException : Exception("User already voted in this poll")
class PollClosedException : Exception("Poll is closed")

/**
 * Polls, votes and the images that belong to them.
 *
 * Images are kept in Firestore as Base64 JPEGs: a thumbnail on the poll document
 * (cheap feed) and the full picture in `polls/{pollId}/images/{imageId}`.
 */
class PollRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    // ---------------------------------------------------------------- reads

    /**
     * Open polls that [uid] is allowed to see – their own and their friends'.
     * `visibleTo` is filled in when the poll is created, so this is a single
     * indexed query rather than a lookup per friend.
     */
    fun activePollsFlow(uid: String): Flow<List<Poll>> = callbackFlow {
        val registration = db.collection(POLLS)
            .whereArrayContains("visibleTo", uid)
            .whereGreaterThan("deadline", Timestamp.now())
            .orderBy("deadline", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Poll::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    /**
     * All polls created by [uid], newest first.
     * Sorting happens in memory so the app does not need a composite index.
     */
    fun myPollsFlow(uid: String): Flow<List<Poll>> = callbackFlow {
        val registration = db.collection(POLLS)
            .whereEqualTo("ownerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val polls = snapshot?.toObjects(Poll::class.java).orEmpty()
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                trySend(polls)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Finished polls that [uid] was allowed to see but did not create – the archive
     * on the profile screen.
     *
     * A poll drops out of the feed the moment its deadline passes, so without this
     * a voter has no way back to the results of a poll they took part in. The polls
     * [uid] created are already listed by [myPollsFlow], so they are filtered out here.
     */
    fun archivedPollsFlow(uid: String): Flow<List<Poll>> = callbackFlow {
        val registration = db.collection(POLLS)
            .whereArrayContains("visibleTo", uid)
            .whereLessThanOrEqualTo("deadline", Timestamp.now())
            .orderBy("deadline", Query.Direction.DESCENDING)
            .limit(ARCHIVE_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val polls = snapshot?.toObjects(Poll::class.java).orEmpty()
                    .filterNot { it.ownerId == uid }
                trySend(polls)
            }
        awaitClose { registration.remove() }
    }

    /** Live updates for one poll – the results screen relies on this. */
    fun pollFlow(pollId: String): Flow<Poll?> = callbackFlow {
        val registration = db.collection(POLLS).document(pollId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Poll::class.java))
            }
        awaitClose { registration.remove() }
    }

    /** Full-size images of a poll: imageId -> Base64 JPEG. */
    suspend fun fullImages(pollId: String): Map<String, String> =
        db.collection(POLLS).document(pollId).collection(IMAGES).get().await()
            .toObjects(PollImageData::class.java)
            .associate { it.id to it.data }

    /** Everyone who voted, newest first. Only the poll owner may read this. */
    fun votesFlow(pollId: String): Flow<List<Vote>> = callbackFlow {
        val registration = db.collection(POLLS).document(pollId).collection(VOTES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val votes = snapshot?.toObjects(Vote::class.java).orEmpty()
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                trySend(votes)
            }
        awaitClose { registration.remove() }
    }

    /** Comments under a poll, oldest first, updating live as friends write. */
    fun commentsFlow(pollId: String): Flow<List<Comment>> = callbackFlow {
        val registration = db.collection(POLLS).document(pollId).collection(COMMENTS)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Comment::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun addComment(pollId: String, uid: String, userName: String, text: String) {
        db.collection(POLLS).document(pollId).collection(COMMENTS)
            .add(
                mapOf(
                    "userId" to uid,
                    "userName" to userName,
                    "text" to text.trim(),
                    "createdAt" to Timestamp.now(),
                )
            ).await()
    }

    suspend fun deleteComment(pollId: String, commentId: String) {
        db.collection(POLLS).document(pollId).collection(COMMENTS).document(commentId)
            .delete().await()
    }

    suspend fun myVote(pollId: String, uid: String): Vote? =
        db.collection(POLLS).document(pollId)
            .collection(VOTES).document(uid)
            .get().await()
            .takeIf { it.exists() }
            ?.toObject(Vote::class.java)

    // --------------------------------------------------------------- writes

    /**
     * Compresses every picked image, writes one document per full-size picture and
     * then creates the poll itself. [onProgress] reports how many images are done.
     */
    suspend fun createPoll(
        context: Context,
        question: String,
        mode: String,
        deadline: Date,
        imageUris: List<Uri>,
        ownerId: String,
        ownerName: String,
        /** Friends of the owner – they plus the owner make up `visibleTo`. */
        friendIds: List<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        val pollRef = db.collection(POLLS).document()
        val images = mutableListOf<PollImage>()

        imageUris.forEachIndexed { index, uri ->
            val imageId = "img_$index"
            val full = ImageCompressor.toBase64(ImageCompressor.fullImage(context, uri))
            val thumb = ImageCompressor.toBase64(ImageCompressor.thumbnail(context, uri))

            pollRef.collection(IMAGES).document(imageId)
                .set(mapOf("data" to full))
                .await()

            images += PollImage(id = imageId, label = ('A' + index).toString(), thumb = thumb)
            onProgress(index + 1, imageUris.size)
        }

        val poll = mapOf(
            "question" to question,
            "ownerId" to ownerId,
            "ownerName" to ownerName,
            "images" to images.map {
                mapOf("id" to it.id, "label" to it.label, "thumb" to it.thumb)
            },
            "mode" to mode,
            "createdAt" to Timestamp.now(),
            "deadline" to Timestamp(deadline),
            "visibleTo" to (listOf(ownerId) + friendIds).distinct(),
            "voteCount" to 0,
            "tally" to images.associate { it.id to 0 },
            "imagesDeleted" to false,
        )
        pollRef.set(poll).await()

        db.collection(AuthRepository.USERS).document(ownerId)
            .update("pollsCreated", FieldValue.increment(1))
            .await()

        pollRef.id
    }

    /**
     * Stores the vote and updates the running tally in a single transaction, so two
     * people voting at the same moment can never overwrite each other's counts.
     */
    suspend fun castVote(
        pollId: String,
        uid: String,
        userName: String,
        choiceImageId: String? = null,
        ratings: Map<String, Int> = emptyMap(),
    ) {
        val pollRef = db.collection(POLLS).document(pollId)
        val voteRef = pollRef.collection(VOTES).document(uid)
        val userRef = db.collection(AuthRepository.USERS).document(uid)

        db.runTransaction { transaction ->
            val existingVote = transaction.get(voteRef)
            val pollSnapshot = transaction.get(pollRef)

            if (existingVote.exists()) throw AlreadyVotedException()
            val poll = pollSnapshot.toObject(Poll::class.java) ?: throw PollClosedException()
            if (poll.isClosed) throw PollClosedException()

            transaction.set(
                voteRef,
                mapOf(
                    "userName" to userName,
                    "choiceImageId" to choiceImageId.orEmpty(),
                    "ratings" to ratings,
                    "createdAt" to Timestamp.now(),
                )
            )

            val updates = mutableMapOf<String, Any>("voteCount" to FieldValue.increment(1))
            if (choiceImageId != null) {
                updates["tally.$choiceImageId"] = FieldValue.increment(1)
            } else {
                ratings.forEach { (imageId, stars) ->
                    updates["tally.$imageId"] = FieldValue.increment(stars.toLong())
                }
            }
            transaction.update(pollRef, updates)
            transaction.update(userRef, "votesGiven", FieldValue.increment(1))
        }.await()
    }

    /** Removes a poll completely: images, votes and the document itself. */
    suspend fun deletePoll(poll: Poll) {
        val pollRef = db.collection(POLLS).document(poll.id)
        deleteImageDocuments(poll.id)
        pollRef.collection(VOTES).get().await()
            .documents.forEach { it.reference.delete().await() }
        pollRef.collection(COMMENTS).get().await()
            .documents.forEach { it.reference.delete().await() }
        pollRef.delete().await()
    }

    /**
     * "התמונות נמחקות בתום הסקר" – for every finished poll of [uid] the pictures are
     * wiped (full images and thumbnails) while the results stay in Firestore.
     * Runs from the feed, so it happens whenever the owner opens the app.
     */
    suspend fun cleanupExpiredPollsOf(uid: String) {
        val snapshot = db.collection(POLLS).whereEqualTo("ownerId", uid).get().await()
        snapshot.toObjects(Poll::class.java)
            .filter { it.isClosed && !it.imagesDeleted }
            .forEach { poll ->
                // Only the full-size copies go. The thumbnails stay on the poll document
                // so the owner keeps their own history – they are ~12KB against ~930KB,
                // so this still reclaims almost all of the space.
                deleteImageDocuments(poll.id)
                db.collection(POLLS).document(poll.id)
                    .update("imagesDeleted", true)
                    .await()
            }
    }

    private suspend fun deleteImageDocuments(pollId: String) {
        db.collection(POLLS).document(pollId).collection(IMAGES).get().await()
            .documents.forEach { it.reference.delete().await() }
    }

    companion object {
        /** How far back the profile archive reaches – enough for a course project. */
        private const val ARCHIVE_LIMIT = 50L

        const val POLLS = "polls"
        const val VOTES = "votes"
        const val COMMENTS = "comments"
        const val IMAGES = "images"
    }
}

package com.hila.snapvote.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/** A registered SnapVote user. Stored in `users/{uid}`. */
data class User(
    @DocumentId val uid: String = "",
    val username: String = "",
    val email: String = "",
    val createdAt: Timestamp? = null,
    val pollsCreated: Int = 0,
    val votesGiven: Int = 0,
)

/**
 * Someone on my friends list – `users/{uid}/friends/{friendUid}`.
 * Friendship is mutual: adding a friend writes the matching document on both sides.
 */
data class Friend(
    @DocumentId val uid: String = "",
    val username: String = "",
    val since: Timestamp? = null,
)

/**
 * One image inside a poll, nested on the poll document.
 *
 * Only the small [thumb] travels with the poll (so the feed costs a single read);
 * the full-size picture lives in its own `polls/{pollId}/images/{imageId}` document,
 * see [PollImageData].
 */
data class PollImage(
    val id: String = "",
    /** "A", "B", "C"… shown to the voter. */
    val label: String = "",
    /** Base64 JPEG, ~320 px. Emptied when the poll ends and the images are deleted. */
    val thumb: String = "",
)

/** The full-size Base64 JPEG, stored one per document to stay under Firestore's 1 MB limit. */
data class PollImageData(
    @DocumentId val id: String = "",
    val data: String = "",
)

/** A poll. Stored in `polls/{pollId}`, votes live in `polls/{pollId}/votes/{uid}`. */
data class Poll(
    @DocumentId val id: String = "",
    val question: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val images: List<PollImage> = emptyList(),
    /** [MODE_SINGLE] or [MODE_RATING]. */
    val mode: String = MODE_SINGLE,
    val createdAt: Timestamp? = null,
    val deadline: Timestamp? = null,
    /**
     * Everyone allowed to see this poll: the owner plus their friends at the moment
     * the poll was created. The feed queries on it and the security rules enforce it.
     */
    val visibleTo: List<String> = emptyList(),
    val voteCount: Int = 0,
    /**
     * imageId -> score. In [MODE_SINGLE] this is the number of votes,
     * in [MODE_RATING] it is the sum of stars (average = score / voteCount).
     */
    val tally: Map<String, Int> = emptyMap(),
    /** True once the images were removed from Storage after the deadline. */
    val imagesDeleted: Boolean = false,
) {
    val isClosed: Boolean
        get() = (deadline?.toDate()?.time ?: 0L) <= System.currentTimeMillis()

    /**
     * Whether [viewerUid] may still see this poll's pictures.
     *
     * The promise the app makes is that your pictures do not pile up on *other people's*
     * phones – not that you lose your own. So a finished poll keeps showing its pictures
     * to the person who created it, in their profile history, and shows none to anybody
     * else from the moment the deadline passes.
     *
     * The full-size copies are deleted from Firestore either way; what the owner keeps
     * seeing are the thumbnails that live on this document.
     */
    fun showsImagesTo(viewerUid: String?): Boolean =
        !isClosed || (viewerUid != null && viewerUid == ownerId)

    val millisLeft: Long
        get() = (deadline?.toDate()?.time ?: 0L) - System.currentTimeMillis()

    /** Winning image by score, or null when nobody voted yet. */
    fun winner(): PollImage? {
        if (voteCount == 0) return null
        val bestId = tally.maxByOrNull { it.value }?.key ?: return null
        return images.firstOrNull { it.id == bestId }
    }

    /** Percentage of the total score that [imageId] holds (0..100). */
    fun percentOf(imageId: String): Int {
        val total = tally.values.sum()
        if (total == 0) return 0
        return Math.round((tally[imageId] ?: 0) * 100f / total)
    }

    /** Average star rating for [imageId] in [MODE_RATING]. */
    fun averageOf(imageId: String): Float {
        if (voteCount == 0) return 0f
        return (tally[imageId] ?: 0).toFloat() / voteCount
    }

    companion object {
        const val MODE_SINGLE = "SINGLE"
        const val MODE_RATING = "RATING"
    }
}

/** A comment friends leave under a poll – `polls/{pollId}/comments/{commentId}`. */
data class Comment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
)

/** A single user's vote inside `polls/{pollId}/votes/{uid}`. */
data class Vote(
    @DocumentId val userId: String = "",
    val userName: String = "",
    /** Chosen image id in [Poll.MODE_SINGLE]. */
    val choiceImageId: String = "",
    /** imageId -> 1..5 stars in [Poll.MODE_RATING]. */
    val ratings: Map<String, Int> = emptyMap(),
    val createdAt: Timestamp? = null,
)

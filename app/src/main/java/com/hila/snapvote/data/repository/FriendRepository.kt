package com.hila.snapvote.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.hila.snapvote.data.model.Friend
import com.hila.snapvote.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FriendNotFoundException : Exception("No user with that username")
class CannotAddSelfException : Exception("Cannot add yourself as a friend")

/**
 * The friends list. Friendship is mutual and immediate: adding someone writes
 * `users/{me}/friends/{them}` *and* `users/{them}/friends/{me}`.
 */
class FriendRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    fun friendsFlow(uid: String): Flow<List<Friend>> = callbackFlow {
        val registration = db.collection(AuthRepository.USERS).document(uid)
            .collection(FRIENDS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val friends = snapshot?.toObjects(Friend::class.java).orEmpty()
                    .sortedBy { it.username }
                trySend(friends)
            }
        awaitClose { registration.remove() }
    }

    /** One-shot read used when a poll is created, to fill in its `visibleTo`. */
    suspend fun friendIds(uid: String): List<String> =
        db.collection(AuthRepository.USERS).document(uid).collection(FRIENDS)
            .get().await()
            .documents.map { it.id }

    /** Looks a person up by their exact username (case-insensitive). */
    suspend fun findByUsername(username: String): User? {
        val name = username.trim().lowercase()
        if (name.isEmpty()) return null

        val entry = db.collection(AuthRepository.USERNAMES).document(name).get().await()
        val uid = entry.getString("uid") ?: return null

        return db.collection(AuthRepository.USERS).document(uid).get().await()
            .toObject(User::class.java)
            ?.copy(uid = uid)
    }

    suspend fun addFriend(myUid: String, myUsername: String, friend: User) {
        if (friend.uid == myUid) throw CannotAddSelfException()

        val now = Timestamp.now()
        val batch = db.batch()
        batch.set(
            db.collection(AuthRepository.USERS).document(myUid)
                .collection(FRIENDS).document(friend.uid),
            mapOf("username" to friend.username, "since" to now)
        )
        batch.set(
            db.collection(AuthRepository.USERS).document(friend.uid)
                .collection(FRIENDS).document(myUid),
            mapOf("username" to myUsername, "since" to now)
        )
        batch.commit().await()
    }

    suspend fun removeFriend(myUid: String, friendUid: String) {
        val batch = db.batch()
        batch.delete(
            db.collection(AuthRepository.USERS).document(myUid)
                .collection(FRIENDS).document(friendUid)
        )
        batch.delete(
            db.collection(AuthRepository.USERS).document(friendUid)
                .collection(FRIENDS).document(myUid)
        )
        batch.commit().await()
    }

    companion object {
        const val FRIENDS = "friends"
    }
}

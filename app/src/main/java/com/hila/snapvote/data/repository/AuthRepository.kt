package com.hila.snapvote.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.hila.snapvote.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UsernameTakenException : Exception("Username already in use")

/** Everything that touches Firebase Authentication and the `users` collection. */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    val currentUid: String?
        get() = auth.currentUser?.uid

    val currentName: String
        get() = auth.currentUser?.displayName.orEmpty()

    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    /**
     * Usernames must be unique so friends can be found by name. The lookup table
     * `usernames/{lowercase name}` is world-readable, which is what lets the register
     * screen check availability before the account exists.
     */
    suspend fun isUsernameAvailable(username: String): Boolean =
        !db.collection(USERNAMES).document(username.lowercase()).get().await().exists()

    /** Creates the auth account, claims the username and writes `users/{uid}`. */
    suspend fun register(username: String, email: String, password: String) {
        if (!isUsernameAvailable(username)) throw UsernameTakenException()

        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = result.user?.uid ?: error("Missing uid after sign up")

        result.user?.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(username).build()
        )?.await()

        // Claiming the name can still lose a race – the rules only allow creating a
        // name document that does not exist yet, so the loser gets an error here.
        try {
            db.collection(USERNAMES).document(username.lowercase())
                .set(mapOf("uid" to uid, "username" to username))
                .await()
        } catch (e: Exception) {
            result.user?.delete()?.await()
            throw UsernameTakenException()
        }

        db.collection(USERS).document(uid).set(
            mapOf(
                "username" to username,
                "usernameLower" to username.lowercase(),
                "email" to email.trim(),
                "createdAt" to Timestamp.now(),
                "pollsCreated" to 0,
                "votesGiven" to 0,
            )
        ).await()
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    fun logout() = auth.signOut()

    /** Live profile document of the signed-in user. */
    fun currentUserFlow(): Flow<User?> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val registration = db.collection(USERS).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(User::class.java))
            }
        awaitClose { registration.remove() }
    }

    companion object {
        const val USERS = "users"
        const val USERNAMES = "usernames"
    }
}

package com.hila.snapvote.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthException
import com.hila.snapvote.data.repository.AuthRepository
import com.hila.snapvote.data.repository.UsernameTakenException
import kotlinx.coroutines.launch

/** Shared by the login and the register screens. */
class AuthViewModel(
    private val repository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _success = MutableLiveData<Boolean>()
    val success: LiveData<Boolean> = _success

    fun login(email: String, password: String) = run {
        _loading.value = true
        viewModelScope.launch {
            runCatching { repository.login(email, password) }
                .onSuccess { _success.value = true }
                .onFailure { _error.value = it.toHebrewMessage() }
            _loading.value = false
        }
    }

    fun register(username: String, email: String, password: String) = run {
        _loading.value = true
        viewModelScope.launch {
            runCatching { repository.register(username, email, password) }
                .onSuccess { _success.value = true }
                .onFailure { _error.value = it.toHebrewMessage() }
            _loading.value = false
        }
    }

    fun errorShown() {
        _error.value = null
    }

    /** Firebase returns English error codes – translate the common ones. */
    private fun Throwable.toHebrewMessage(): String = when {
        this is UsernameTakenException ->
            "שם המשתמש הזה כבר תפוס, נסי אחר"
        this is FirebaseAuthException && errorCode == "ERROR_INVALID_CREDENTIAL" ->
            "אימייל או סיסמה שגויים"
        this is FirebaseAuthException && errorCode == "ERROR_USER_NOT_FOUND" ->
            "לא נמצא משתמש עם האימייל הזה"
        this is FirebaseAuthException && errorCode == "ERROR_WRONG_PASSWORD" ->
            "הסיסמה שגויה"
        this is FirebaseAuthException && errorCode == "ERROR_EMAIL_ALREADY_IN_USE" ->
            "האימייל הזה כבר רשום במערכת"
        this is FirebaseAuthException && errorCode == "ERROR_WEAK_PASSWORD" ->
            "הסיסמה חלשה מדי – לפחות 6 תווים"
        this is FirebaseAuthException && errorCode == "ERROR_INVALID_EMAIL" ->
            "כתובת אימייל לא תקינה"
        message?.contains("network", ignoreCase = true) == true ->
            "אין חיבור לאינטרנט"
        else -> message ?: "משהו השתבש, נסי שוב"
    }
}

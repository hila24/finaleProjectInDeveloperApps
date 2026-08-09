package com.hila.snapvote

import android.app.Application
import com.google.firebase.FirebaseApp
import com.hila.snapvote.util.PollNotifications

class SnapVoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        PollNotifications.createChannel(this)
    }
}

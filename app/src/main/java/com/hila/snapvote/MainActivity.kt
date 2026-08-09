package com.hila.snapvote

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.hila.snapvote.data.repository.AuthRepository
import com.hila.snapvote.databinding.ActivityMainBinding
import com.hila.snapvote.ui.common.pollArgs
import com.hila.snapvote.util.PendingPoll

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navController = navHost.navController

        // Already signed in? Skip the login screen entirely.
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(
            if (AuthRepository().isLoggedIn) R.id.feedFragment else R.id.loginFragment
        )
        navController.graph = graph

        binding.bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible =
                destination.id == R.id.feedFragment || destination.id == R.id.profileFragment
        }

        if (savedInstanceState == null) handleLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    /**
     * A shared poll link (or a tapped reminder) lands here. When nobody is signed in
     * the id waits in [PendingPoll] until the login screen is done with.
     */
    private fun handleLink(intent: Intent?) {
        val pollId = intent?.data?.let(::pollIdFrom) ?: return
        intent.data = null

        if (AuthRepository().isLoggedIn) {
            // A malformed or stale link should do nothing, not take the app down.
            runCatching { navController.navigate(R.id.voteFragment, pollArgs(pollId)) }
        } else {
            PendingPoll.save(this, pollId)
        }
    }

    /** Accepts both `https://…/poll/{id}` and `snapvote://poll/{id}`. */
    private fun pollIdFrom(uri: Uri): String? = when {
        uri.scheme == "snapvote" && uri.host == "poll" ->
            uri.lastPathSegment?.takeIf { it.isNotBlank() }
        uri.pathSegments.size >= 2 && uri.pathSegments[0] == "poll" ->
            uri.pathSegments[1].takeIf { it.isNotBlank() }
        else -> null
    }
}

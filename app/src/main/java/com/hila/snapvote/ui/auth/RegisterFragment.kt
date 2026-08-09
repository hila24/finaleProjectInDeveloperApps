package com.hila.snapvote.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.hila.snapvote.R
import com.hila.snapvote.databinding.FragmentRegisterBinding
import com.hila.snapvote.ui.common.BaseFragment

class RegisterFragment : BaseFragment<FragmentRegisterBinding>(FragmentRegisterBinding::inflate) {

    private val viewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.registerButton.setOnClickListener { submit() }
        binding.goToLoginButton.setOnClickListener { findNavController().popBackStack() }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progress.isVisible = loading
            binding.registerButton.isEnabled = !loading
        }
        observeMessage(viewModel.error, viewModel::errorShown)
        viewModel.success.observe(viewLifecycleOwner) { success ->
            if (success == true) openFeedOrPendingPoll(findNavController(), requireContext())
        }
    }

    private fun submit() {
        val username = binding.usernameInput.text?.toString().orEmpty().trim()
        val email = binding.emailInput.text?.toString().orEmpty().trim()
        val password = binding.passwordInput.text?.toString().orEmpty()

        binding.usernameLayout.error = null
        binding.emailLayout.error = null
        binding.passwordLayout.error = null

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showMessage(R.string.error_empty_fields)
            return
        }
        if (username.length < 2) {
            binding.usernameLayout.error = getString(R.string.error_short_username)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.error_invalid_email)
            return
        }
        if (password.length < 6) {
            binding.passwordLayout.error = getString(R.string.error_short_password)
            return
        }
        viewModel.register(username, email, password)
    }
}

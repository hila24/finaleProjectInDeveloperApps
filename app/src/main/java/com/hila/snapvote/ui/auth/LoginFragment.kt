package com.hila.snapvote.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.hila.snapvote.R
import com.hila.snapvote.databinding.FragmentLoginBinding
import com.hila.snapvote.ui.common.BaseFragment

class LoginFragment : BaseFragment<FragmentLoginBinding>(FragmentLoginBinding::inflate) {

    private val viewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.loginButton.setOnClickListener { submit() }
        binding.goToRegisterButton.setOnClickListener {
            navigateSafely(R.id.action_login_to_register)
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progress.isVisible = loading
            binding.loginButton.isEnabled = !loading
        }
        observeMessage(viewModel.error, viewModel::errorShown)
        viewModel.success.observe(viewLifecycleOwner) { success ->
            if (success == true) openFeedOrPendingPoll(findNavController(), requireContext())
        }
    }

    private fun submit() {
        val email = binding.emailInput.text?.toString().orEmpty().trim()
        val password = binding.passwordInput.text?.toString().orEmpty()

        binding.emailLayout.error = null
        binding.passwordLayout.error = null

        if (email.isEmpty() || password.isEmpty()) {
            showMessage(R.string.error_empty_fields)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.error_invalid_email)
            return
        }
        viewModel.login(email, password)
    }
}

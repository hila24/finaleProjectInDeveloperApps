package com.hila.snapvote.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar

/**
 * The parts every screen in the app repeats: creating the binding, dropping it when the
 * view goes away, showing a message, and navigating without crashing.
 *
 * A subclass passes its generated binding's `inflate` and gets [binding] for free:
 *
 * ```
 * class FeedFragment : BaseFragment<FragmentFeedBinding>(FragmentFeedBinding::inflate)
 * ```
 */
abstract class BaseFragment<VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> VB,
) : Fragment() {

    private var _binding: VB? = null

    protected val binding: VB
        get() = checkNotNull(_binding) { "binding was used outside the view lifecycle" }

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Subclasses that release adapters should touch [binding] *before* calling super,
     * because this is where it is cleared.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    protected fun showMessage(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
    }

    protected fun showMessage(@StringRes textRes: Int) {
        Snackbar.make(binding.root, textRes, Snackbar.LENGTH_LONG).show()
    }

    /** Shows a one-shot error from a ViewModel and tells it the message was seen. */
    protected fun observeMessage(source: LiveData<String?>, onShown: () -> Unit) {
        source.observe(viewLifecycleOwner) { message ->
            if (message == null) return@observe
            showMessage(message)
            onShown()
        }
    }

    /**
     * Navigation that survives a double trigger.
     *
     * Two observers can fire in the same frame – "the vote was saved" and "show the
     * results instead" both lead away from the voting screen. The second call would
     * then run from a destination the action does not belong to, which throws. Checking
     * first turns that crash into a no-op.
     */
    protected fun navigateSafely(@IdRes actionId: Int, args: Bundle? = null) {
        val controller = findNavController()
        if (controller.currentDestination?.getAction(actionId) != null) {
            controller.navigate(actionId, args)
        }
    }
}

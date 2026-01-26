package com.tianma.xsmscode.common.fragment.backpress

import android.content.Context
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment

open class BackPressFragment : Fragment() {

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (interceptBackPress()) {
                // Intercepted
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                isEnabled = true // Re-enable if we want to intercept again later
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().onBackPressedDispatcher.addCallback(this, backCallback)
    }

    open fun interceptBackPress(): Boolean {
        return false
    }

    // Called by subclasses to toggle interception
    protected fun setBackPressEnabled(enabled: Boolean) {
        backCallback.isEnabled = enabled
    }
}

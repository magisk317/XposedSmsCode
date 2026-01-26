package com.tianma.xsmscode.ui.faq

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment

/**
 * FAQ fragment migrated to Jetpack Compose
 */
class FaqFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            val items = FaqItemContainer(context).faqItems
            setContent {
                FaqScreen(items = items)
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(): FaqFragment {
            return FaqFragment()
        }
    }
}

package com.github.magisk317.smscode.ui.home.update

import androidx.activity.ComponentActivity
import io.github.magisk317.uikit.shell.PlayUpdateDelegate

class FlavorPlayUpdateDelegate : PlayUpdateDelegate {

    override fun onCreate(activity: ComponentActivity, onFallbackToStore: () -> Unit) = Unit

    override fun onResume(activity: ComponentActivity, onFallbackToStore: () -> Unit) = Unit

    override fun onDestroy() = Unit

    override fun requestUpdate(
        activity: ComponentActivity,
        silentIfNoUpdate: Boolean,
        fallbackOnQueryFailure: Boolean,
        onFallbackToStore: () -> Unit,
    ) {
        if (!silentIfNoUpdate || fallbackOnQueryFailure) {
            onFallbackToStore()
        }
    }
}

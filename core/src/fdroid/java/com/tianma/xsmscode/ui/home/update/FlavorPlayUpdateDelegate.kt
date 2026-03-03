package com.tianma.xsmscode.ui.home.update

import androidx.appcompat.app.AppCompatActivity

class FlavorPlayUpdateDelegate : PlayUpdateDelegate {

    override fun onCreate(activity: AppCompatActivity, onFallbackToStore: () -> Unit) = Unit

    override fun onResume(activity: AppCompatActivity, onFallbackToStore: () -> Unit) = Unit

    override fun onDestroy() = Unit

    override fun requestUpdate(
        activity: AppCompatActivity,
        silentIfNoUpdate: Boolean,
        fallbackOnQueryFailure: Boolean,
        onFallbackToStore: () -> Unit,
    ) {
        if (!silentIfNoUpdate || fallbackOnQueryFailure) {
            onFallbackToStore()
        }
    }
}

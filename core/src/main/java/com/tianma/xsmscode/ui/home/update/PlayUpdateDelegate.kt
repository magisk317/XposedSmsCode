package com.tianma.xsmscode.ui.home.update

import androidx.appcompat.app.AppCompatActivity

interface PlayUpdateDelegate {
    fun onCreate(activity: AppCompatActivity, onFallbackToStore: () -> Unit)

    fun onResume(activity: AppCompatActivity, onFallbackToStore: () -> Unit)

    fun onDestroy()

    fun requestUpdate(
        activity: AppCompatActivity,
        silentIfNoUpdate: Boolean,
        fallbackOnQueryFailure: Boolean,
        onFallbackToStore: () -> Unit,
    )
}

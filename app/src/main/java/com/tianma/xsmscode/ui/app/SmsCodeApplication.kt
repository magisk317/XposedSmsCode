package com.tianma.xsmscode.ui.app

import android.app.Application
import com.tianma.xsmscode.feature.migrate.TransitionTask
import com.google.android.material.color.DynamicColors
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.Executors

class SmsCodeApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        installDefaultEventBus()
        applyTheme()
        DynamicColors.applyToActivitiesIfAvailable(this)
        performTransitionTask()
    }

    private fun applyTheme() {
        val mode = com.tianma.xsmscode.common.utils.SPUtils.getThemeMode(this)
        val nightMode = when (mode) {
            1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun installDefaultEventBus() {
        // EventBus index is disabled temporarily to support Java 25 (KAPT incompatible)
        EventBus.builder().installDefaultEventBus()
    }

    private fun performTransitionTask() {
        val singlePool = Executors.newSingleThreadExecutor()
        singlePool.execute(TransitionTask(this))
    }
}

package com.github.magisk317.smscode.ui.home.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.github.magisk317.smscode.runtime.RuntimePlayAction
import com.github.magisk317.smscode.runtime.RuntimeUpdateFacade

class FlavorPlayUpdateDelegate : PlayUpdateDelegate {

    private var appUpdateManager: AppUpdateManager? = null
    private var updateLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var installStateUpdatedListener: InstallStateUpdatedListener? = null

    override fun onCreate(activity: ComponentActivity, onFallbackToStore: () -> Unit) {
        val manager = AppUpdateManagerFactory.create(activity)
        appUpdateManager = manager
        updateLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                onFallbackToStore()
            }
        }

        val listener = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                manager.completeUpdate()
            }
        }
        installStateUpdatedListener = listener
        manager.registerListener(listener)
    }

    override fun onResume(activity: ComponentActivity, onFallbackToStore: () -> Unit) {
        val manager = appUpdateManager ?: return
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startUpdateFlow(manager, info, onFallbackToStore)
            } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
                manager.completeUpdate()
            }
        }
    }

    override fun onDestroy() {
        val manager = appUpdateManager
        val listener = installStateUpdatedListener
        if (manager != null && listener != null) {
            manager.unregisterListener(listener)
        }
        installStateUpdatedListener = null
        appUpdateManager = null
        updateLauncher = null
    }

    override fun requestUpdate(
        activity: ComponentActivity,
        silentIfNoUpdate: Boolean,
        fallbackOnQueryFailure: Boolean,
        onFallbackToStore: () -> Unit,
    ) {
        val manager = appUpdateManager ?: return
        manager.appUpdateInfo.addOnSuccessListener { info ->
            val action = RuntimeUpdateFacade.decidePlayAction(
                updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
                flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                inProgress = info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
                silentIfNoUpdate = silentIfNoUpdate,
            )
            when (action) {
                RuntimePlayAction.START_UPDATE_FLOW -> startUpdateFlow(manager, info, onFallbackToStore)
                RuntimePlayAction.OPEN_STORE_OR_GITHUB -> onFallbackToStore()
                RuntimePlayAction.NO_OP -> Unit
            }
        }.addOnFailureListener {
            when (RuntimeUpdateFacade.decidePlayFailureAction(fallbackOnQueryFailure)) {
                RuntimePlayAction.OPEN_STORE_OR_GITHUB -> onFallbackToStore()
                else -> Unit
            }
        }
    }

    private fun startUpdateFlow(
        manager: AppUpdateManager,
        info: AppUpdateInfo,
        onFallbackToStore: () -> Unit,
    ) {
        val launcher = updateLauncher
        if (launcher == null) {
            onFallbackToStore()
            return
        }
        try {
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
            )
        } catch (_: Exception) {
            onFallbackToStore()
        }
    }
}

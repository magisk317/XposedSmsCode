package com.tianma.xsmscode.ui.home.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.tianma.xsmscode.data.update.UpdateCoordinator

class FlavorPlayUpdateDelegate : PlayUpdateDelegate {

    private var appUpdateManager: AppUpdateManager? = null
    private var updateLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var installStateUpdatedListener: InstallStateUpdatedListener? = null

    override fun onCreate(activity: AppCompatActivity, onFallbackToStore: () -> Unit) {
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

    override fun onResume(activity: AppCompatActivity, onFallbackToStore: () -> Unit) {
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
        activity: AppCompatActivity,
        silentIfNoUpdate: Boolean,
        fallbackOnQueryFailure: Boolean,
        onFallbackToStore: () -> Unit,
    ) {
        val manager = appUpdateManager ?: return
        manager.appUpdateInfo.addOnSuccessListener { info ->
            val action = UpdateCoordinator.decidePlayAction(
                updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
                flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                inProgress = info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
                silentIfNoUpdate = silentIfNoUpdate,
            )
            when (action) {
                UpdateCoordinator.PlayAction.START_UPDATE_FLOW -> startUpdateFlow(manager, info, onFallbackToStore)
                UpdateCoordinator.PlayAction.OPEN_STORE_OR_GITHUB -> onFallbackToStore()
                UpdateCoordinator.PlayAction.NO_OP -> Unit
            }
        }.addOnFailureListener {
            when (UpdateCoordinator.decidePlayFailureAction(fallbackOnQueryFailure)) {
                UpdateCoordinator.PlayAction.OPEN_STORE_OR_GITHUB -> onFallbackToStore()
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

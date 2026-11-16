package io.github.magisk317.smscode.di

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.magisk317.smscode.feature.backup.CloudBackupRestoreHooks
import io.github.magisk317.smscode.feature.backup.SmsCodeCloudBackupAccess
import io.github.magisk317.smscode.feature.backup.SmsCodeCloudBackupPayloadSource
import io.github.magisk317.smscode.runtime.RuntimeBackupFacade
import io.github.magisk317.smscode.runtime.RuntimeCodeRecordFacade
import io.github.magisk317.smscode.runtime.RuntimeNotificationFacade
import io.github.magisk317.smscode.runtime.AppPrefsFacade
import io.github.magisk317.smscode.runtime.RuntimeStorageFacade
import io.github.magisk317.smscode.runtime.RuntimeStoreFacade
import io.github.magisk317.smscode.runtime.bridge.UiBackupAccess
import io.github.magisk317.smscode.runtime.bridge.UiCloudBackupAccess
import io.github.magisk317.smscode.runtime.bridge.UiCodeRecordAccess
import io.github.magisk317.smscode.runtime.bridge.UiNotificationAccess
import io.github.magisk317.smscode.runtime.bridge.UiPrefsAccess
import io.github.magisk317.smscode.runtime.bridge.UiStorageAccess
import io.github.magisk317.smscode.runtime.bridge.UiStoreAccess
import io.github.magisk317.smscode.ui.home.LauncherActivity
import io.github.magisk317.xposed.backup.BackupPayloadSource
import io.github.magisk317.xposed.backup.webdav.WebDavCloudBackupAccess
import io.github.magisk317.xposed.logging.XLog
import org.koin.dsl.module

val appModule = module {
    single<UiStorageAccess> { RuntimeStorageFacade }
    single<UiStoreAccess> { RuntimeStoreFacade }
    single<UiBackupAccess> { RuntimeBackupFacade }
    single<UiCodeRecordAccess> { RuntimeCodeRecordFacade }
    single<UiNotificationAccess> { RuntimeNotificationFacade }
    single<UiPrefsAccess> { AppPrefsFacade }

    // Cloud backup: the runtime owns the payload format and the WebDAV provider
    // seam; the app shell contributes the launcher-icon side effect that lives
    // in the presentation layer.
    single<BackupPayloadSource> {
        SmsCodeCloudBackupPayloadSource(
            restoreHooks = CloudBackupRestoreHooks(
                applyLauncherIconVisible = ::applyLauncherIconVisible,
            ),
        )
    }
    single {
        WebDavCloudBackupAccess(
            payloadSource = get<BackupPayloadSource>(),
            defaultRemotePath = SmsCodeCloudBackupAccess.DEFAULT_CLOUD_REMOTE_PATH,
        )
    }
    single<UiCloudBackupAccess> { SmsCodeCloudBackupAccess(get()) }
}

private fun applyLauncherIconVisible(context: Context, visible: Boolean): Boolean {
    val component = ComponentName(context, LauncherActivity::class.java)
    val newState = if (visible) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
    return runCatching {
        context.packageManager.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
    }.onFailure {
        XLog.e(message = "Failed to set launcher icon visible=%s", visible)
    }.isSuccess
}

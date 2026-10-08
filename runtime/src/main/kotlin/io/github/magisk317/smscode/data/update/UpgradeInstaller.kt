package io.github.magisk317.smscode.data.update

import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.runtime.common.update.UpgradeInstaller as SharedUpgradeInstaller
import java.io.File

object UpgradeInstaller {

    fun canRequestPackageInstalls(context: Context): Boolean = SharedUpgradeInstaller.canRequestPackageInstalls(context)

    fun buildUnknownSourceSettingsIntent(context: Context): Intent =
        SharedUpgradeInstaller.buildUnknownSourceSettingsIntent(context)

    fun installApk(context: Context, apkFile: File): Result<Unit> =
        SharedUpgradeInstaller.installApk(context, apkFile)
}

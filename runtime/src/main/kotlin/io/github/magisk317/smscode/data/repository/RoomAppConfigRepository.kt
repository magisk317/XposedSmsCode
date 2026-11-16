package io.github.magisk317.smscode.data.repository

import io.github.magisk317.smscode.data.db.DBManager
import io.github.magisk317.smscode.data.db.entity.AppInfo
import io.github.magisk317.smscode.runtime.bridge.UiAppConfig
import io.github.magisk317.smscode.runtime.bridge.UiAppConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App config storage on the Room DAO, speaking the UI [UiAppConfig] DTO.
 *
 * The UI never sees the Room entity; this adapter is the one place that translates,
 * which keeps the app list screens free of storage plumbing. The DTO mirrors the
 * entity's serialization shape, so the file mirror written from it stays compatible
 * with the Xposed hook's fallback reader.
 */
class RoomAppConfigRepository(
    private val dbManager: DBManager,
) : UiAppConfigRepository {

    override suspend fun getAll(): List<UiAppConfig> =
        dbManager.queryAllAppInfosSuspend().map { it.toUiConfig() }

    override suspend fun upsert(config: UiAppConfig) = withContext(Dispatchers.IO) {
        dbManager.upsertAppInfo(config.toEntity())
        Unit
    }

    override suspend fun removeByPackages(packageNames: List<String>) = withContext(Dispatchers.IO) {
        dbManager.removeAppInfosByPackage(packageNames)
        Unit
    }
}

internal fun AppInfo.toUiConfig(): UiAppConfig = UiAppConfig(
    packageName = packageName,
    label = label,
    blocked = blocked,
)

internal fun UiAppConfig.toEntity(): AppInfo = AppInfo(
    packageName = packageName,
    label = label,
    blocked = blocked,
)

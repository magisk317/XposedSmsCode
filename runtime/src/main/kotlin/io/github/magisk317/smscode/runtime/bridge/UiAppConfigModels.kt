package io.github.magisk317.smscode.runtime.bridge

import kotlinx.serialization.Serializable

/**
 * UI-facing app configuration row used by the app list screen.
 *
 * Property names intentionally mirror the Room
 * [io.github.magisk317.smscode.data.db.entity.AppInfo] serialization shape
 * (packageName/label/blocked), so files written from this DTO stay byte-compatible
 * with the JSON the Xposed hook fallback reads for its provider-unavailable path.
 */
@Serializable
data class UiAppConfig(
    val packageName: String = "",
    val label: String? = null,
    val blocked: Boolean = false,
)

/**
 * App config CRUD for the app list screens, expressed in [UiAppConfig].
 *
 * Implementations must be safe to call from the main thread; suspending members are
 * expected to switch to their own dispatcher.
 */
interface UiAppConfigRepository {
    suspend fun getAll(): List<UiAppConfig>

    suspend fun upsert(config: UiAppConfig)

    suspend fun removeByPackages(packageNames: List<String>)
}

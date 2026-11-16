package io.github.magisk317.smscode.feature.backup

import android.content.Context
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.common.constant.PrefRestoreTypeRegistry
import io.github.magisk317.smscode.common.constant.PrefValueType
import io.github.magisk317.smscode.common.utils.AppPreferenceTransactions
import io.github.magisk317.smscode.common.utils.HookPreferenceSpecs
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceCommitResult
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceSpec
import io.github.magisk317.xposed.logging.XLog
import java.util.Locale

/** Side effects the cloud restore applies after preferences are committed. */
data class CloudBackupRestoreHooks(
    val applyLauncherIconVisible: (Context, Boolean) -> Boolean = { _, _ -> true },
)

/**
 * Applies a restored preference map with the same typed guards the SAF restore
 * uses: entitlement state is runtime-derived and skipped, values are coerced to
 * their declared types before writing, and the commit result is verified.
 */
object CloudBackupRestorePreferences {

    data class CoercedRestoreValue(
        val type: PrefValueType,
        val booleanValue: Boolean? = null,
        val intValue: Int? = null,
        val floatValue: Float? = null,
        val stringValue: String? = null,
    ) {
        val shouldWrite: Boolean
            get() = when (type) {
                PrefValueType.BOOLEAN -> booleanValue != null
                PrefValueType.INT -> intValue != null
                PrefValueType.FLOAT -> floatValue != null
                PrefValueType.STRING -> true
            }
    }

    suspend fun restore(
        context: Context,
        preferences: Map<String, String?>,
        hooks: CloudBackupRestoreHooks = CloudBackupRestoreHooks(),
    ) {
        if (preferences.isEmpty()) return
        var launcherVisible: Boolean? = null
        val result = AppPreferenceTransactions.commit(context) {
            for ((key, rawValue) in preferences) {
                if (key == PrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN ||
                    key == PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED
                ) {
                    XLog.w(message = "Cloud restore preference skipped: entitlement state is runtime-derived")
                    continue
                }
                if (rawValue == null) continue
                val coerced = coerceRestoreValue(key, rawValue)
                if (!coerced.shouldWrite) {
                    XLog.w(
                        message = "Cloud restore preference skipped: key=%s raw=%s expectedType=%s",
                        key,
                        rawValue,
                        coerced.type.name,
                    )
                    continue
                }
                when (coerced.type) {
                    PrefValueType.BOOLEAN -> {
                        val value = coerced.booleanValue ?: continue
                        set(resolveBooleanRestoreSpec(key, value), value)
                        if (key == PrefConst.KEY_SHOW_LAUNCHER_ICON) launcherVisible = value
                    }

                    PrefValueType.INT -> {
                        val value = coerced.intValue ?: continue
                        set(PreferenceSpec.int(key, value), value)
                    }

                    PrefValueType.FLOAT -> {
                        val value = coerced.floatValue ?: continue
                        set(PreferenceSpec.float(key, value), value)
                    }

                    PrefValueType.STRING -> {
                        val value = coerced.stringValue ?: rawValue
                        set(resolveStringRestoreSpec(key, value), value)
                    }
                }
            }
        }
        when (result) {
            is PreferenceCommitResult.Persisted -> {
                result.postCommitFailures.forEach { failure ->
                    XLog.e(
                        message = "Cloud restore preference post-commit hook failed: hook=%s error=%s",
                        failure.hookName,
                        failure.error.message ?: failure.error.javaClass.simpleName,
                    )
                }
                launcherVisible?.let { visible ->
                    val applied = hooks.applyLauncherIconVisible(context, visible)
                    if (!applied) {
                        XLog.w(message = "Cloud restore launcher icon apply failed: visible=%s", visible)
                    }
                }
            }
            is PreferenceCommitResult.NoChanges -> Unit
            is PreferenceCommitResult.NotPersisted -> {
                throw IllegalStateException("Restored preferences were not persisted", result.error)
            }
        }
    }

    private fun resolveBooleanRestoreSpec(key: String, restoredValue: Boolean): PreferenceSpec<Boolean> =
        when (key) {
            PrefConst.KEY_ENABLE_AUTO_INPUT_CODE -> HookPreferenceSpecs.autoInputEnabled
            PrefConst.KEY_ENABLE_AUTO_ENTER_CODE -> HookPreferenceSpecs.autoEnterEnabled
            else -> PreferenceSpec.boolean(key, restoredValue)
        }

    private fun resolveStringRestoreSpec(key: String, restoredValue: String): PreferenceSpec<String> =
        when (key) {
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY -> HookPreferenceSpecs.autoInputDelay
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL -> HookPreferenceSpecs.autoInputInterval
            else -> PreferenceSpec.string(key, restoredValue)
        }

    @JvmStatic
    fun coerceRestoreValue(key: String, rawValue: String): CoercedRestoreValue {
        return when (PrefRestoreTypeRegistry.typeOf(key)) {
            PrefValueType.BOOLEAN -> CoercedRestoreValue(
                type = PrefValueType.BOOLEAN,
                booleanValue = parseBooleanValue(rawValue),
            )

            PrefValueType.INT -> CoercedRestoreValue(
                type = PrefValueType.INT,
                intValue = rawValue.trim().toIntOrNull(),
            )

            PrefValueType.FLOAT -> CoercedRestoreValue(
                type = PrefValueType.FLOAT,
                floatValue = rawValue.trim().toFloatOrNull(),
            )

            PrefValueType.STRING -> CoercedRestoreValue(
                type = PrefValueType.STRING,
                stringValue = rawValue,
            )
        }
    }

    @JvmStatic
    fun parseBooleanValue(rawValue: String): Boolean? {
        return when (rawValue.trim().lowercase(Locale.ROOT)) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> null
        }
    }
}

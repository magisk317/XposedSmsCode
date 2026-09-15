package com.github.magisk317.smscode.common.utils

import android.content.Context
import com.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.contract.prefs.AtomicPreferencePersistence
import io.github.magisk317.smscode.runtime.common.prefs.PreferenceCommitCoordinator
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceCommitResult
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceEditScope
import io.github.magisk317.smscode.runtime.contract.prefs.PreferencePostCommitHooks
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceSpec

/** Parent-owned keys and defaults for the high-value auto-input settings transaction. */
object HookPreferenceSpecs {
    val autoInputEnabled: PreferenceSpec<Boolean> =
        PreferenceSpec.boolean(PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, defaultValue = true)
    val autoEnterEnabled: PreferenceSpec<Boolean> =
        PreferenceSpec.boolean(PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, defaultValue = false)
    val autoInputDelay: PreferenceSpec<String> =
        PreferenceSpec.string(PrefConst.KEY_AUTO_INPUT_CODE_DELAY, PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT)
    val autoInputInterval: PreferenceSpec<String> =
        PreferenceSpec.string(PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL, PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT)
}

/** Coordinates durable writes before cross-process preference publication. */
object AppPreferenceTransactions {
    suspend fun commit(
        context: Context,
        persistence: AtomicPreferencePersistence = AtomicPreferencePersistence { changes ->
            AppPreferencesDataStore.persistChanges(context, changes)
        },
        edit: PreferenceEditScope.() -> Unit,
    ): PreferenceCommitResult = createHookPreferenceCommitCoordinator(
        persistence = persistence,
        publish = {
            check(HookPreferenceMirror.publish(context)) { "Hook preference publication failed" }
        },
    ).commit(edit)
}

internal fun createHookPreferenceCommitCoordinator(
    persistence: AtomicPreferencePersistence,
    publish: suspend () -> Unit,
): PreferenceCommitCoordinator = PreferenceCommitCoordinator(
    persistence = persistence,
    hooks = listOf(
        PreferencePostCommitHooks.publish { publish() },
    ),
)

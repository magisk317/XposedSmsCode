package io.github.magisk317.smscode.security

import android.content.Context
import com.magisk317.mobile.entitlement.MobileEntitlementCoordinator
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.common.utils.HookPreferenceMirror
import io.github.magisk317.smscode.presentation.BuildConfig
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.uikit.entitlement.resetLastKnownAutomationAllowed
import io.github.magisk317.uikit.update.security.CiUpdateGateConfig
import io.github.magisk317.uikit.update.security.CiUpdateGateVaultCore
import io.github.magisk317.uikit.update.security.GateEntitlementOps
import io.github.magisk317.uikit.update.security.GateHookPrefsPublisher
import io.github.magisk317.uikit.update.security.GatePrefEditsApplier
import io.github.magisk317.uikit.update.security.GatePrefEdits
import io.github.magisk317.uikit.update.security.GatePrefKeys

/** Product wiring for the shared CI update gate vault (ui-kit:update). */
object CiUpdateGateVault {
    // Vault payloads written before version codes were recorded are treated as upgraded once
    // the running build reaches this floor; XSC baseline.
    private const val UPGRADED_BASELINE_VERSION_CODE = 135L

    private val core = CiUpdateGateVaultCore(
        CiUpdateGateConfig(
            keys = GatePrefKeys(
                enableKey = PrefConst.KEY_ENABLE,
                automationAllowedKey = PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
                tokenKey = PrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN,
            ),
            upgradedBaselineVersionCode = UPGRADED_BASELINE_VERSION_CODE,
            versionCode = { BuildConfig.VERSION_CODE.toLong() },
            commitHash = { BuildConfig.COMMIT_HASH },
            prefEditsApplier = object : GatePrefEditsApplier {
                override suspend fun apply(context: Context, block: suspend GatePrefEdits.() -> Unit) {
                    AppPreferencesDataStore.batchEdit(context) {
                        block(object : GatePrefEdits {
                            override suspend fun setBoolean(key: String, value: Boolean) {
                                this@batchEdit.setBoolean(key, value)
                            }

                            override suspend fun setString(key: String, value: String) {
                                this@batchEdit.setString(key, value)
                            }
                        })
                    }
                }
            },
            hookPrefsPublisher = object : GateHookPrefsPublisher {
                override suspend fun publish(context: Context): Boolean =
                    HookPreferenceMirror.publish(context)
            },
            entitlement = object : GateEntitlementOps {
                override suspend fun refresh(context: Context) {
                    MobileEntitlementCoordinator.refresh(context, force = true)
                }
            },
            onAutomationAllowedChanged = { resetLastKnownAutomationAllowed(it) },
        ),
    )

    @JvmStatic
    fun backup(context: Context) = core.backup(context)

    @JvmStatic
    fun restoreIfEligible(context: Context): Boolean = core.restoreIfEligible(context)
}

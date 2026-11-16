package io.github.magisk317.smscode.xp.hook.mms

import android.content.Context
import io.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.smscode.common.utils.SmsCodeUtils
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseSourceKind
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.xposed.hook.vendor.MiuiBugleReadStateHost
import io.github.magisk317.smscode.xp.helper.ModuleConflictArbiter

/**
 * XSC wiring for the shared MIUI bugle read-state hook (smscode-core vendor package).
 */
object XscBugleReadStateHost : MiuiBugleReadStateHost {
    override val applicationId: String = BuildConfig.APPLICATION_ID

    override fun isModuleEnabled(context: Context): Boolean =
        HookRuntimeBridge.prefsAccess.isEnabled(context)

    override fun isMobileAutomationAllowed(context: Context): Boolean =
        HookRuntimeBridge.prefsAccess.mobileAutomationAllowed(context)

    override fun isMarkAsReadEnabled(context: Context): Boolean =
        HookRuntimeBridge.prefsAccess.markAsReadEnabled(context)

    override fun shouldSuppressByRelay(context: Context, tag: String): Boolean =
        ModuleConflictArbiter.shouldSuppressByRelay(context, tag)

    override suspend fun parseVerificationCode(context: Context, body: String): String? =
        SmsCodeUtils.parseSmsCodeIfExists(
            context,
            body,
            source = SmsCodeParseSource(
                packageName = context.packageName,
                sourceKind = SmsCodeParseSourceKind.SMS,
            ),
        ).takeIf { it.isNotBlank() }
}

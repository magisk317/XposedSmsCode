package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.github.magisk317.smscode.common.utils.HookPrefsReader
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.xposed.utils.XLog
import com.github.magisk317.smscode.data.db.entity.AppInfo
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.feature.store.EntityStoreManager
import com.github.magisk317.smscode.feature.store.EntityType
import io.github.magisk317.smscode.runtime.verification.AutoInputActionHelper
import io.github.magisk317.smscode.runtime.verification.AutoInputBlockedPackageHelper
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction
import com.github.magisk317.smscode.xp.hook.code.helper.InputHelper
import com.github.magisk317.smscode.xp.hook.code.VerificationSmsMsg
import com.github.magisk317.smscode.xp.hook.code.toVerificationMessage

/**
 * 自动输入验证码
 */
class AutoInputAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val deduplicateEnabled: Boolean? = null,
    private val dispatchDelayMs: Long = 0L,
    private val attemptId: Long? = null,
) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        AutoInputActionHelper<VerificationSmsMsg>(
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsMsg = mSmsMsg.toVerificationMessage(),
            deduplicateEnabled = deduplicateEnabled,
            dispatchDelayMs = dispatchDelayMs,
            deduplicateReader = HookPrefsReader::deduplicateSms,
            sharedGateClaimer = { context, fileName, key, windowMs, maxEntries ->
                HookRuntimeBridge.contentProviderAccess.claimRuntimeGate(
                    context = context,
                    fileName = fileName,
                    keys = key,
                    windowMs = windowMs,
                    maxEntries = maxEntries,
                ).let { claim ->
                    AutoInputActionHelper.ClaimResult(
                        claimed = claim.claimed,
                        ageMs = claim.ageMs,
                    )
                }
            },
            packageBlockedChecker = ::isPackageBlocked,
            autoEnterReader = HookPrefsReader::autoEnterCodeEnabled,
            inputIntervalReader = HookPrefsReader::getAutoInputCodeIntervalMs,
            attemptRecorder = { smsMsg, foregroundPackage ->
                recordAutoInputAttempt(smsMsg.raw, foregroundPackage)
            },
            inputSender = InputHelper::sendText,
        ).run()
        return null
    }

    private fun recordAutoInputAttempt(smsMsg: SmsMsg, foregroundPackage: String?): Long? {
        return runCatching {
            // Cross-uid access to the module's private Room DB file is unreliable
            // (permission/frozen/SELinux), so write through the DBProvider instead.
            // record_id is intentionally left null: the provider's sms_msg query
            // whitelist does not support a sender+body+date fingerprint lookup, and
            // auto_input_event.record_id is nullable. Recording the attempt reliably
            // takes priority over the (secondary) record association.
            val uri = HookRuntimeBridge.contentProviderAccess.autoInputEventContentUri(mPluginContext)
            val values = ContentValues().apply {
                if (attemptId != null && attemptId > 0L) {
                    put("id", attemptId)
                }
                put("package_name", foregroundPackage)
                put("code_length", smsMsg.smsCode?.length ?: 0)
                // Attempt wall-clock time (not smsMsg.date): date is the SMS receive
                // timestamp and is only useful for record association, which we no
                // longer resolve across UIDs.
                put("attempt_at", System.currentTimeMillis())
            }
            val insertedUri = mPluginContext.contentResolver.insert(uri, values)
            insertedUri?.lastPathSegment?.toLongOrNull()
        }.onFailure { error ->
            XLog.w(
                "Insert auto input attempt failed: %s",
                error.message ?: error.javaClass.simpleName,
            )
        }.getOrNull() ?: attemptId
    }

    private fun isPackageBlocked(packageName: String): Boolean {
        return AutoInputBlockedPackageHelper.resolveBlockedState(
            packageName = packageName,
            primaryChecker = ::queryBlockedStateByProvider,
            fallbackChecker = { pkg ->
                EntityStoreManager.loadEntitiesFromFile(
                    mPluginContext,
                    EntityType.APP_CONFIG,
                    AppInfo::class.java,
                ).any { it.packageName == pkg && it.blocked }
            },
            fallbackLogger = { pkg, blocked ->
                XLog.d("AutoInput fallback file check: pkg=%s blocked=%s", pkg, blocked)
            },
        )
    }

    private fun queryBlockedStateByProvider(packageName: String): Boolean? {
        val blocked = AutoInputBlockedPackageHelper.queryBlockedStateByProvider(
            context = mPluginContext,
            packageName = packageName,
            uriResolver = { pkg -> Uri.withAppendedPath(HookRuntimeBridge.contentProviderAccess.appInfoContentUri(mPluginContext), pkg) },
        )
        return blocked?.also {
            XLog.d("AutoInput provider check: pkg=%s blocked=%s", packageName, it)
        } ?: run {
            XLog.w("AutoInput provider check failed: pkg=%s err=provider_query_failed", packageName)
            null
        }
    }

}

package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import com.github.magisk317.smscode.runtime.RuntimeStorageFacade
import io.github.magisk317.smscode.runtime.common.utils.SharedRuntimeGate
import io.github.magisk317.smscode.xposed.utils.XLog
import com.github.magisk317.smscode.data.db.DBProvider
import com.github.magisk317.smscode.data.db.entity.AppInfo
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.feature.store.EntityStoreManager
import com.github.magisk317.smscode.feature.store.EntityType
import io.github.magisk317.smscode.verification.AutoInputActionHelper
import io.github.magisk317.smscode.verification.AutoInputBlockedPackageHelper
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
            deduplicateReader = PrefsReader::deduplicateSms,
            sharedGateClaimer = { context, fileName, key, windowMs, maxEntries ->
                SharedRuntimeGate.claimAllWithinWindow(
                    context = context,
                    fileName = fileName,
                    keys = key,
                    windowMs = windowMs,
                    maxEntries = maxEntries,
                ).toShared()
            },
            packageBlockedChecker = ::isPackageBlocked,
            autoEnterReader = PrefsReader::autoEnterCodeEnabled,
            inputIntervalReader = PrefsReader::getAutoInputCodeIntervalMs,
            attemptRecorder = { smsMsg, foregroundPackage ->
                recordAutoInputAttempt(smsMsg.raw, foregroundPackage)
            },
            inputSender = InputHelper::sendText,
        ).run()
        return null
    }

    private fun recordAutoInputAttempt(smsMsg: SmsMsg, foregroundPackage: String?): Long? {
        return runCatching {
            val db = RuntimeStorageFacade.dbManager(mPluginContext)
            val timestamp = if (smsMsg.date > 0L) smsMsg.date else System.currentTimeMillis()
            val recordId = db.querySmsMsgByFingerprint(
                sender = smsMsg.sender,
                body = smsMsg.body,
                date = timestamp,
                msgType = SmsMsg.MSG_TYPE_SMS,
            )?.id
            db.insertAutoInputAttempt(
                id = attemptId,
                recordId = recordId,
                packageName = foregroundPackage,
                codeLength = smsMsg.smsCode?.length ?: 0,
            )
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
            uriResolver = { pkg -> Uri.withAppendedPath(DBProvider.appInfoContentUri(mPluginContext), pkg) },
        )
        return blocked?.also {
            XLog.d("AutoInput provider check: pkg=%s blocked=%s", packageName, it)
        } ?: run {
            XLog.w("AutoInput provider check failed: pkg=%s err=provider_query_failed", packageName)
            null
        }
    }

    private fun SharedRuntimeGate.ClaimResult.toShared(): AutoInputActionHelper.ClaimResult {
        return AutoInputActionHelper.ClaimResult(
            claimed = claimed,
            ageMs = ageMs,
        )
    }
}

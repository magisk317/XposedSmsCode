package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.common.utils.HookPrefsReader
import com.github.magisk317.smscode.common.utils.SmsCodeUtils
import io.github.magisk317.smscode.runtime.common.utils.StringUtils
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.rule.utils.SmsCodeParsedMetadataResolver
import io.github.magisk317.smscode.runtime.verification.SmsParseAction as SharedSmsParseAction
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction
import com.github.magisk317.smscode.xp.hook.code.VerificationSmsMsg
import com.github.magisk317.smscode.xp.hook.code.toVerificationMessage

/**
 * 解析短信中的验证码
 */
class SmsParseAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg?) :
    CallableAction(pluginContext, phoneContext, smsMsg ?: SmsMsg()) {

    private var mSmsIntent: Intent? = null
    private var mDeduplicateEnabled: Boolean = false

    fun setSmsIntent(smsIntent: Intent?) {
        mSmsIntent = smsIntent
    }

    fun setDeduplicateEnabled(enabled: Boolean) {
        mDeduplicateEnabled = enabled
    }

    override fun action(): android.os.Bundle? {
        val outcome = SharedSmsParseAction<VerificationSmsMsg>(
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsIntent = mSmsIntent,
            deduplicateEnabled = mDeduplicateEnabled,
            incomingSmsParser = { SmsMsg.fromIntent(it).toVerificationMessage() },
            sensitiveDebugLogReader = HookPrefsReader::isSensitiveDebugLogMode,
            summarizeSender = { sensitive, value ->
                if (sensitive) StringUtils.escape(value).orEmpty() else StringUtils.summarizeSender(value)
            },
            summarizeBody = { sensitive, value ->
                if (sensitive) StringUtils.escape(value).orEmpty() else StringUtils.summarizeBody(value)
            },
            summarizeCode = { sensitive, value ->
                if (sensitive) StringUtils.escape(value).orEmpty() else StringUtils.summarizeCode(value)
            },
            duplicateChecker = { sender, body, timestamp ->
                runCatching {
                    HookRuntimeBridge.storageAccess.dbManager(mPluginContext).querySmsMsgByFingerprint(sender, body, timestamp) != null
                }.getOrDefault(false)
            },
            preparedSmsResolver = { pluginContext, phoneContext, smsMsg, _, timestamp ->
                val msgBody = smsMsg.raw.body.orEmpty()
                val smsCode = SmsCodeUtils.parseSmsCodeIfExists(pluginContext, msgBody)
                if (smsCode.isBlank()) {
                    return@SharedSmsParseAction null
                }
                val metadata = SmsCodeParsedMetadataResolver.resolve(
                    body = msgBody,
                    parseCompanyCandidates = SmsCodeUtils::parseCompanyCandidates,
                    parseCompany = SmsCodeUtils::parseCompany,
                    findPackageNameByLabel = { label ->
                        SmsCodeUtils.findPackageNameByLabel(phoneContext, label)
                    },
                )
                smsMsg.raw.copy(
                    smsCode = smsCode,
                    company = metadata.company,
                    date = timestamp,
                    packageName = metadata.packageName,
                ).toVerificationMessage()
            },
        ).parse() ?: return null
        mSmsMsg = outcome.smsMsg?.raw ?: mSmsMsg
        return outcome.toBundle()
    }

    companion object {
        const val SMS_MSG = SharedSmsParseAction.KEY_SMS_MSG
        const val SMS_DUPLICATED = SharedSmsParseAction.KEY_SMS_DUPLICATED
    }
}

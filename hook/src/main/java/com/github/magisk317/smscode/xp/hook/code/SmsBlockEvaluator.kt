package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.common.utils.HookPrefsReader
import com.github.magisk317.smscode.common.utils.SmsBlacklistUtils
import com.github.magisk317.smscode.common.utils.SmsCodeUtils
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.runtime.verification.SmsBlockEvaluator as SharedSmsBlockEvaluator

object SmsBlockEvaluator {
    const val BLOCK_REASON_BLACKLIST = "blacklist_block"
    const val BLOCK_REASON_PREF_BLOCK = "pref_block_sms"

    data class Result(
        val smsMsg: SmsMsg?,
        val blockReason: String?,
        val blacklistDeleteOnly: Boolean,
    )

    private val delegate = SharedSmsBlockEvaluator(
        incomingSmsParser = { intent -> SmsMsg.fromIntent(intent).toVerificationMessage() },
        blacklistMatcher = { context, sender, body ->
            SmsBlacklistUtils.match(context, sender, body).toVerificationResult()
        },
        blockSmsEnabledReader = HookPrefsReader::blockSmsEnabled,
        smsCodeParser = { context, body -> SmsCodeUtils.parseSmsCodeIfExists(context, body) },
    )

    fun evaluate(
        pluginContext: Context,
        intent: Intent,
        eventId: String,
        source: String,
    ): Result? {
        val result = delegate.evaluate(
            pluginContext = pluginContext,
            intent = intent,
            eventId = eventId,
            source = source,
        ) ?: return null
        return Result(
            smsMsg = result.smsMsg?.raw,
            blockReason = result.blockReasonWireValue,
            blacklistDeleteOnly = result.blacklistDeleteOnly,
        )
    }

    private fun SmsBlacklistUtils.MatchResult.toVerificationResult(): BlacklistMatchResult {
        return BlacklistMatchResult(
            matched = matched,
            matchType = matchType,
            pattern = pattern,
            actionDelete = actionDelete,
            actionBlock = actionBlock,
        )
    }
}

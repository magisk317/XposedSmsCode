package com.github.magisk317.smscode.common.utils

import android.content.Context
import io.github.magisk317.smscode.rule.model.SmsBlacklistConfig
import io.github.magisk317.smscode.runtime.common.sms.RuntimeSmsBlacklistAdapter
import io.github.magisk317.smscode.runtime.common.sms.SmsBlacklistConfigProvider

object SmsBlacklistUtils {

    data class MatchResult(
        val matched: Boolean,
        val matchType: String? = null,
        val pattern: String? = null,
        val actionDelete: Boolean = false,
        val actionBlock: Boolean = false,
    )

    private val adapter = RuntimeSmsBlacklistAdapter(
        configProvider = SmsBlacklistConfigProvider { context ->
            SmsBlacklistConfig(
                enabled = HookPrefsReader.smsBlacklistEnabled(context),
                actionDelete = HookPrefsReader.smsBlacklistActionDelete(context),
                actionBlock = HookPrefsReader.smsBlacklistActionBlock(context),
                numbers = HookPrefsReader.smsBlacklistNumbers(context),
                prefixes = HookPrefsReader.smsBlacklistPrefixes(context),
                content = HookPrefsReader.smsBlacklistContent(context),
                regex = HookPrefsReader.smsBlacklistRegex(context),
            )
        },
    )

    @JvmStatic
    fun match(context: Context, sender: String?, body: String?): MatchResult {
        val sharedResult = adapter.match(context, sender, body)
        return MatchResult(
            matched = sharedResult.matched,
            matchType = sharedResult.matchType,
            pattern = sharedResult.pattern,
            actionDelete = sharedResult.actionDelete,
            actionBlock = sharedResult.actionBlock,
        )
    }
}

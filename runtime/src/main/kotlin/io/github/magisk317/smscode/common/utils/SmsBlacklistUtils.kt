package io.github.magisk317.smscode.common.utils

import android.content.Context
import io.github.magisk317.smscode.rule.model.SmsBlacklistConfig
import io.github.magisk317.smscode.runtime.common.sms.RuntimeSmsBlacklistAdapter
import io.github.magisk317.smscode.runtime.common.sms.SmsBlacklistConfigProvider
import io.github.magisk317.smscode.verification.BlacklistMatchResult

/**
 * Blacklist lookup for XSC.
 *
 * Matching lives in core; this only supplies the pref-backed config and returns the
 * shared result type, so there is one MatchResult definition instead of two.
 */
// Runtime/Xposed only. Do not use from UI/app-side business logic.
object SmsBlacklistUtils {

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
    fun match(context: Context, sender: String?, body: String?): BlacklistMatchResult =
        adapter.match(context, sender, body)
}

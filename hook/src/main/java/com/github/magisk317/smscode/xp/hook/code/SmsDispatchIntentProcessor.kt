package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.common.utils.SmsBlacklistUtils
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.verification.SmsDispatchIntentProcessor as SharedSmsDispatchIntentProcessor
import io.github.magisk317.smscode.verification.SmsHandlerDispatchDecision

internal class SmsDispatchIntentProcessor(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val incomingSmsParser: (Intent) -> VerificationSmsMsg? = { SmsMsg.fromIntent(it).toVerificationMessage() },
    private val blacklistMatcher: (Context, String?, String?) -> BlacklistMatchResult = { context, sender, body ->
        val result = SmsBlacklistUtils.match(context, sender, body)
        BlacklistMatchResult(
            matched = result.matched,
            matchType = result.matchType,
            pattern = result.pattern,
            actionDelete = result.actionDelete,
            actionBlock = result.actionBlock,
        )
    },
    private val codeParser: (Context, Context, Intent, String) -> ParseResult? = { resolvedPluginContext, resolvedPhoneContext, intent, eventId ->
        CodeWorker(resolvedPluginContext, resolvedPhoneContext, intent, eventId).parse()
    },
    private val delegateFactory: (
        Context,
        Context,
        (Intent) -> VerificationSmsMsg?,
        (Context, String?, String?) -> BlacklistMatchResult,
        (Context, Context, Intent, String) -> ParseResult?,
    ) -> SharedSmsDispatchIntentProcessor<VerificationSmsMsg> = { resolvedPluginContext, resolvedPhoneContext, incomingParser, matcher, parser ->
        SharedSmsDispatchIntentProcessor(
            pluginContext = resolvedPluginContext,
            phoneContext = resolvedPhoneContext,
            incomingSmsParser = incomingParser,
            blacklistMatcher = matcher,
            codeParser = parser,
        )
    },
) {
    data class Outcome(
        val smsMsg: SmsMsg?,
        val blacklistResult: BlacklistMatchResult,
        val parseResult: ParseResult?,
        val decision: SmsHandlerDispatchDecision.Decision,
    )

    fun handle(intent: Intent, eventId: String): Outcome {
        val outcome = delegateFactory(
            pluginContext,
            phoneContext,
            incomingSmsParser,
            blacklistMatcher,
            codeParser,
        ).handle(intent, eventId)
        return Outcome(
            smsMsg = outcome.smsMsg?.raw,
            blacklistResult = outcome.blacklistResult,
            parseResult = outcome.parseResult as? ParseResult,
            decision = outcome.decision,
        )
    }
}

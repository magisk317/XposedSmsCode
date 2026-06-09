package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.content.Intent
import androidx.core.os.BundleCompat
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.CodeWorker as SharedCodeWorker
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.verification.SmsParseActionRunner
import io.github.magisk317.smscode.xposed.utils.XLog
import com.github.magisk317.smscode.xp.hook.code.action.impl.KillMeAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.SmsParseAction
import java.util.concurrent.TimeUnit

class CodeWorker(
    private val mPluginContext: Context,
    private val mPhoneContext: Context,
    private val mSmsIntent: Intent,
    private val eventId: String = "",
) {
    fun parse(): ParseResult? {
        var lastAttemptId: Long? = null
        return SharedCodeWorker<VerificationSmsMsg, ParseResult>(
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsIntent = mSmsIntent,
            eventId = eventId,
            settingsLoader = { context -> SmsCodePostParseCoordinator.loadSettings(SmsCodeVerificationPrefs(context)) },
            moduleEnabledReader = PrefsReader::isEnabled,
            verboseLogReader = PrefsReader::isVerboseLogMode,
            logLevelSetter = XLog::setLogLevel,
            currentLogLevelReader = XLog::getLogLevel,
            defaultLogLevel = BuildConfig.LOG_LEVEL,
            parseRunner = ::runSmsParseAction,
            parsedSmsDispatcher = { uiHandler, executor, pluginContext, phoneContext, smsMsg, eventId, plan ->
                val attemptId = if (plan.autoInputDelayMs != null) System.currentTimeMillis() else null
                lastAttemptId = attemptId
                SmsCodeActionDispatcher.dispatchParsedSmsActions(
                    uiHandler = uiHandler,
                    executor = executor,
                    pluginContext = pluginContext,
                    phoneContext = phoneContext,
                    smsMsg = smsMsg.raw,
                    eventId = eventId,
                    plan = plan,
                    attemptId = attemptId,
                )
            },
            afterDispatch = { executor, pluginContext, phoneContext, smsMsg, plan ->
                if (PrefsReader.killMeEnabled(pluginContext)) {
                    scheduleKillMe(executor, pluginContext, phoneContext, smsMsg.raw, plan, lastAttemptId)
                }
            },
            parseResultFactory = ::buildParseResult,
        ).parse()
    }

    private fun scheduleKillMe(
        executor: java.util.concurrent.ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        plan: SmsCodePostParseCoordinator.ParsedSmsPlan,
        attemptId: Long?,
    ) {
        val autoInputDelayMs = plan.autoInputDelayMs
        if (autoInputDelayMs == null) {
            XLog.w("KillMe enabled but auto-input disabled, skip KillMeAction")
            return
        }
        val notificationAutoCancelDelayMs = plan.notificationPlan?.autoCancelDelayMs
        val killDelayMs = maxOf(autoInputDelayMs + 1500L, 2500L)
        val killMeAction = KillMeAction(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            attemptId = attemptId,
            notificationAutoCancelDelayMs = notificationAutoCancelDelayMs,
        )
        killMeAction.armAutoInputResultListener()
        executor.schedule(
            killMeAction,
            killDelayMs,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun runSmsParseAction(
        executor: java.util.concurrent.ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsIntent: Intent,
        deduplicateEnabled: Boolean,
    ): SharedCodeWorker.ParseOutcome<VerificationSmsMsg>? {
        val smsParseAction = SmsParseAction(pluginContext, phoneContext, null)
        smsParseAction.setSmsIntent(smsIntent)
        smsParseAction.setDeduplicateEnabled(deduplicateEnabled)

        return SmsParseActionRunner.runWithTimeout(
            executor = executor,
            runAction = smsParseAction::action,
            duplicatedReader = { parseBundle -> parseBundle.getBoolean(SmsParseAction.SMS_DUPLICATED, false) },
            messageReader = { parseBundle ->
                val verificationSmsMsg = BundleCompat.getParcelable(
                    parseBundle,
                    SmsParseAction.SMS_MSG,
                    VerificationSmsMsg::class.java,
                )
                val smsMsg = verificationSmsMsg?.raw
                    ?: BundleCompat.getParcelable(parseBundle, SmsParseAction.SMS_MSG, SmsMsg::class.java)
                    ?: return@runWithTimeout null
                smsMsg.toVerificationMessage()
            },
        )
    }

    private fun buildParseResult(blockSms: Boolean): ParseResult {
        return ParseResult().apply { isBlockSms = blockSms }
    }
}

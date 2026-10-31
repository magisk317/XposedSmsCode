package io.github.magisk317.smscode.xp.hook.helper

import android.content.Context
import io.github.magisk317.smscode.common.utils.SmsCodeUtils
import io.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.runtime.common.utils.StringUtils
import io.github.magisk317.xposed.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.xposed.hook.telephony.SmsHookCapabilities

/**
 * XSC 侧的统一宿主能力适配器。
 *
 * core 的共享 hook 层只依赖 [HookHostCapabilities]；XSC 的运行时桥接是
 * [HookRuntimeBridge]（prefs/notification/storage/codeRecord/contentProvider +
 * hookProcessInit），本对象把这层差异收敛在一处，使 XSC 与 xinyi-relay
 * 消费同一份契约。
 */
internal object XscHookHostCapabilities : SmsHookCapabilities {
    override fun isVerboseLogMode(context: Context): Boolean =
        BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD

    override fun isSensitiveDebugLogMode(context: Context): Boolean =
        BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD

    override fun escapeCode(value: String): String = StringUtils.escape(value).orEmpty()

    override fun summarizeCode(value: String): String = StringUtils.summarizeCode(value)

    override fun escapeBody(value: String): String = StringUtils.escape(value).orEmpty()

    override fun summarizeBody(value: String): String = StringUtils.summarizeBody(value)

    override suspend fun parseSmsCode(context: Context, content: String): String =
        SmsCodeUtils.parseSmsCodeIfExists(context, content)

    override fun onProcessInit(context: Context) {
        HookRuntimeBridge.hookProcessInit?.invoke(context)
    }

    override fun recordHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
    ) {
        HookRuntimeBridge.contentProviderAccess.recordHookHeartbeat(
            context = context,
            packageName = packageName,
            processName = processName,
            source = source,
            verboseLogging = verboseLogging,
            route = LogRoute.SMS_HOOK.id,
        )
    }
}

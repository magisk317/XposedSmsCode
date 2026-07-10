package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import io.github.magisk317.smscode.runtime.common.utils.ClipboardUtils
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.CopyToClipboardActionHelper
import com.github.magisk317.smscode.xp.hook.code.action.RunnableAction

/**
 * 将验证码复制到剪切板
 */
class CopyToClipboardAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val enabled: Boolean? = null,
) : RunnableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        if (enabled ?: HookRuntimeBridge.prefsAccess.copyToClipboardEnabled(mPluginContext)) {
            CopyToClipboardActionHelper.copyCode(
                phoneContext = mPhoneContext,
                smsCode = mSmsMsg.smsCode,
                copyAction = ClipboardUtils::copyToClipboard,
            )
        }
        return null
    }
}

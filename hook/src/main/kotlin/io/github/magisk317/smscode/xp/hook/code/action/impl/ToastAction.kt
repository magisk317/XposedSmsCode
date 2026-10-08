package io.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import io.github.magisk317.smscode.hook.R
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.xp.hook.code.helper.InputHelper
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.verification.ToastActionHelper
import io.github.magisk317.smscode.xp.hook.code.action.RunnableAction
import io.github.magisk317.smscode.xp.hook.code.toVerificationMessage

/**
 * 显示验证码Toast
 */
class ToastAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val enabled: Boolean? = null,
) : RunnableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        ToastActionHelper.showCodeToast(
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsMsg = mSmsMsg.toVerificationMessage(),
            enabled = enabled ?: HookRuntimeBridge.prefsAccess.shouldShowToast(mPluginContext),
            messageTextProvider = { context, smsCode ->
                context.getString(R.string.hook_current_sms_code, smsCode)
            },
            fallbackToastSender = InputHelper::sendToast,
            duration = Toast.LENGTH_LONG,
        )
        return null
    }
}

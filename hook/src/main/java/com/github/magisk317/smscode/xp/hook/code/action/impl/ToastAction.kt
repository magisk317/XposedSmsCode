package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.github.magisk317.smscode.hook.R
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import com.github.magisk317.smscode.xp.hook.code.helper.InputHelper
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.ToastActionHelper
import com.github.magisk317.smscode.xp.hook.code.action.RunnableAction
import com.github.magisk317.smscode.xp.hook.code.toVerificationMessage

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

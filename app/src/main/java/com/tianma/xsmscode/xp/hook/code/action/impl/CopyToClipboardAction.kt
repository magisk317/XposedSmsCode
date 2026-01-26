package com.tianma.xsmscode.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import com.tianma.xsmscode.common.utils.ClipboardUtils
import com.tianma.xsmscode.common.utils.XSPUtils
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.RunnableAction
import de.robv.android.xposed.XSharedPreferences

/**
 * 将验证码复制到剪切板
 */
class CopyToClipboardAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    xsp: XSharedPreferences
) : RunnableAction(pluginContext, phoneContext, smsMsg, xsp) {

    override fun action(): Bundle? {
        if (XSPUtils.copyToClipboardEnabled(xsp)) {
            copyToClipboard()
        }
        return null
    }

    private fun copyToClipboard() {
        ClipboardUtils.copyToClipboard(mPluginContext, mSmsMsg.smsCode)
    }
}

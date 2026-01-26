package com.tianma.xsmscode.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.utils.XSPUtils
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.RunnableAction
import de.robv.android.xposed.XSharedPreferences

/**
 * 显示验证码Toast
 */
class ToastAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    xsp: XSharedPreferences
) : RunnableAction(pluginContext, phoneContext, smsMsg, xsp) {

    override fun action(): Bundle? {
        if (XSPUtils.shouldShowToast(xsp)) {
            showCodeToast()
        }
        return null
    }

    private fun showCodeToast() {
        val text = mPluginContext.getString(R.string.current_sms_code, mSmsMsg.smsCode)
        mPhoneContext?.let {
            Toast.makeText(it, text, Toast.LENGTH_LONG).show()
        }
    }
}

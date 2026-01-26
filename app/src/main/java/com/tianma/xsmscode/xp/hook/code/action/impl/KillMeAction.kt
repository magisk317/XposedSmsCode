package com.tianma.xsmscode.xp.hook.code.action.impl

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.common.utils.XSPUtils
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.CallableAction
import de.robv.android.xposed.XSharedPreferences

class KillMeAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    xsp: XSharedPreferences
) : CallableAction(pluginContext, phoneContext, smsMsg, xsp) {

    override fun action(): Bundle? {
        killMe()
        return null
    }

    private fun killMe() {
        if (XSPUtils.killMeEnabled(xsp)) {
            killBackgroundProcess(BuildConfig.APPLICATION_ID)
        }
    }

    /**
     * android.app.ActivityManager#killBackgroundProcess()
     */
    @SuppressLint("MissingPermission")
    private fun killBackgroundProcess(packageName: String) {
        try {
            val activityManager = mPluginContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            activityManager?.let {
                it.killBackgroundProcesses(packageName)
                XLog.d("Kill %s background process succeed", packageName)
            }
        } catch (e: Throwable) {
            XLog.e("Error occurs when kill background process %s", packageName, e)
        }
    }
}

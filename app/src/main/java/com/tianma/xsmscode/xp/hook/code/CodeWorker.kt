package com.tianma.xsmscode.xp.hook.code

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.os.BundleCompat
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.common.utils.XSPUtils
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.impl.*
import de.robv.android.xposed.XSharedPreferences
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CodeWorker(
    private val mPluginContext: Context,
    private val mPhoneContext: Context,
    private val mSmsIntent: Intent
) {
    private val xsp: XSharedPreferences = XSharedPreferences(BuildConfig.APPLICATION_ID, PrefConst.PREF_NAME)
    private val mUIHandler: Handler = Handler(Looper.getMainLooper())
    private val mScheduledExecutor = Executors.newSingleThreadScheduledExecutor()

    fun parse(): ParseResult? {
        if (!XSPUtils.isEnabled(xsp)) {
            XLog.i("XposedSmsCode disabled, exiting")
            return null
        }

        val verboseLog = XSPUtils.isVerboseLogMode(xsp)
        if (verboseLog) {
            XLog.setLogLevel(Log.VERBOSE)
        } else {
            XLog.setLogLevel(BuildConfig.LOG_LEVEL)
        }

        val smsParseAction = SmsParseAction(mPluginContext, mPhoneContext, null, xsp)
        smsParseAction.setSmsIntent(mSmsIntent)
        val smsParseFuture = mScheduledExecutor.schedule(smsParseAction, 0, TimeUnit.MILLISECONDS)

        val smsMsg: SmsMsg
        try {
            val parseBundle = smsParseFuture.get() ?: return null

            val duplicated = parseBundle.getBoolean(SmsParseAction.SMS_DUPLICATED, false)
            if (duplicated) {
                return buildParseResult()
            }

            smsMsg = BundleCompat.getParcelable(parseBundle, SmsParseAction.SMS_MSG, SmsMsg::class.java) ?: return null
        } catch (e: Exception) {
            XLog.e("Error occurs when get SmsParseAction call value", e)
            return null
        }

        // 复制到剪切板 Action
        mUIHandler.post(CopyToClipboardAction(mPluginContext, mPhoneContext, smsMsg, xsp))

        // 显示Toast Action
        mUIHandler.post(ToastAction(mPluginContext, mPhoneContext, smsMsg, xsp))

        // 自动输入 Action
        if (XSPUtils.autoInputCodeEnabled(xsp)) {
            val autoInputAction = AutoInputAction(mPluginContext, mPhoneContext, smsMsg, xsp)
            val autoInputDelay = XSPUtils.getAutoInputCodeDelay(xsp) * 1000L
            mScheduledExecutor.schedule(autoInputAction, autoInputDelay, TimeUnit.MILLISECONDS)
        }

        // 显示通知 Action
        val notifyAction = NotifyAction(mPluginContext, mPhoneContext, smsMsg, xsp)
        val notificationFuture = mScheduledExecutor.schedule(notifyAction, 0, TimeUnit.MILLISECONDS)

        // 记录验证码短信 Action
        val recordSmsAction = RecordSmsAction(mPluginContext, mPhoneContext, smsMsg, xsp)
        mScheduledExecutor.schedule(recordSmsAction, 0, TimeUnit.MILLISECONDS)

        // 操作验证码短信（标记为已读 或者 删除） Action
        val operateSmsAction = OperateSmsAction(mPluginContext, mPhoneContext, smsMsg, xsp)
        mScheduledExecutor.schedule(operateSmsAction, 3000, TimeUnit.MILLISECONDS)

        // 自杀 Action
        val killMeAction = KillMeAction(mPluginContext, mPhoneContext, smsMsg, xsp)
        mScheduledExecutor.schedule(killMeAction, 4000, TimeUnit.MILLISECONDS)

        try {
            // 清除通知
            val bundle = notificationFuture.get()
            if (bundle != null && bundle.containsKey(NotifyAction.NOTIFY_RETENTION_TIME)) {
                val delay = bundle.getLong(NotifyAction.NOTIFY_RETENTION_TIME, 0L)
                val notificationId = bundle.getInt(NotifyAction.NOTIFY_ID, 0)
                val cancelNotifyAction = CancelNotifyAction(mPluginContext, mPhoneContext, smsMsg, xsp)
                cancelNotifyAction.setNotificationId(notificationId)

                mScheduledExecutor.schedule(cancelNotifyAction, delay, TimeUnit.MILLISECONDS)
            }
        } catch (e: Exception) {
            XLog.e("Error in notification future get()", e)
        }

        return buildParseResult()
    }

    private fun buildParseResult(): ParseResult {
        val parseResult = ParseResult()
        parseResult.isBlockSms = XSPUtils.blockSmsEnabled(xsp)
        return parseResult
    }
}

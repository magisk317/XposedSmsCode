package com.tianma.xsmscode.xp.hook.code.action.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.utils.SmsCodeUtils
import com.tianma.xsmscode.common.utils.StringUtils
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.common.utils.XSPUtils
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.feature.store.EntityStoreManager
import com.tianma.xsmscode.feature.store.EntityType
import com.tianma.xsmscode.xp.hook.code.action.CallableAction
import de.robv.android.xposed.XSharedPreferences
import kotlin.math.abs

/**
 * 解析短信中的验证码
 */
class SmsParseAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg?,
    xsp: XSharedPreferences
) : CallableAction(pluginContext, phoneContext, smsMsg ?: SmsMsg(), xsp) {

    private var mSmsIntent: Intent? = null

    fun setSmsIntent(smsIntent: Intent?) {
        mSmsIntent = smsIntent
    }

    override fun action(): Bundle? {
        return parseSmsMsg()
    }

    private fun parseSmsMsg(): Bundle? {
        val intent = mSmsIntent ?: return null
        val smsMsg = SmsMsg.fromIntent(intent)
        // Update the member variable of super class if possible, but it's val. 
        // Actually, CallableAction should have var mSmsMsg or we use the local one.
        // Wait, CallableAction has @JvmField protected val mSmsMsg.
        // I'll use a local variable and update fields of mSmsMsg if it's not final in Java.
        // But in Kotlin it's val.
        
        val sender = smsMsg.sender
        val msgBody = smsMsg.body
        
        if (BuildConfig.DEBUG) {
            XLog.d("Sender: %s", sender)
            XLog.d("Body: %s", msgBody)
        } else {
            XLog.d("Sender: %s", StringUtils.escape(sender))
            XLog.d("Body: %s", StringUtils.escape(msgBody))
        }

        if (TextUtils.isEmpty(sender) || TextUtils.isEmpty(msgBody)) {
            return null
        }

        val msgBodyNotNull = msgBody ?: ""
        val smsCode = SmsCodeUtils.parseSmsCodeIfExists(mPluginContext, msgBodyNotNull, true)
        if (TextUtils.isEmpty(smsCode)) { // isn't code message
            return null
        }

        smsMsg.smsCode = smsCode
        smsMsg.company = SmsCodeUtils.parseCompany(msgBodyNotNull)
        val timestamp = System.currentTimeMillis()
        smsMsg.date = timestamp

        // Sync with mSmsMsg so other actions can use it
        mSmsMsg.sender = smsMsg.sender
        mSmsMsg.body = smsMsg.body
        mSmsMsg.date = smsMsg.date
        mSmsMsg.company = smsMsg.company
        mSmsMsg.smsCode = smsMsg.smsCode

        val bundle = Bundle()
        bundle.putParcelable(SMS_MSG, mSmsMsg)

        // 去除重复短信
        var duplicated = false
        if (XSPUtils.deduplicateSms(xsp)) {
            val prevSmsMsg = EntityStoreManager.loadEntityFromFile(EntityType.PREV_SMS_MSG, SmsMsg::class.java)
            if (prevSmsMsg != null) {
                if (abs(timestamp - prevSmsMsg.date) <= 15000) {
                    if ((sender == prevSmsMsg.sender && smsCode == prevSmsMsg.smsCode)
                        || msgBody == prevSmsMsg.body
                    ) {
                        duplicated = true
                        XLog.d("Duplicated message, ignore")
                    }
                }
            }
            // 保存当前验证码记录 Action
            EntityStoreManager.storeEntityToFile(EntityType.PREV_SMS_MSG, mSmsMsg)
        }

        bundle.putBoolean(SMS_DUPLICATED, duplicated)
        return bundle
    }

    companion object {
        const val SMS_MSG = "sms_msg"
        const val SMS_DUPLICATED = "sms_duplicated"
    }
}

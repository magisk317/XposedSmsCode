package com.tianma.xsmscode.xp.hook.code

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.provider.Telephony
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.core.R
import com.tianma.xsmscode.common.constant.NotificationConst
import com.tianma.xsmscode.common.utils.ModuleActivationStore
import com.tianma.xsmscode.common.utils.NotificationUtils
import com.tianma.xsmscode.common.utils.PrefsReader
import com.tianma.xsmscode.common.utils.SmsBlacklistUtils
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.helper.XposedWrapper
import com.tianma.xsmscode.xp.hook.BaseHook
import com.tianma.xsmscode.xp.hook.code.action.impl.OperateSmsAction
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Hook class com.android.internal.telephony.InboundSmsHandler
 */
class SmsHandlerHook : BaseHook() {

    private var mPhoneContext: Context? = null
    private var mPluginContext: Context? = null

    override fun onLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (ANDROID_PHONE_PACKAGE == lpparam.packageName) {
            XLog.i("SmsCode initializing")
            printDeviceInfo()
            try {
                hookSmsHandler(lpparam.classLoader)
            } catch (e: Throwable) {
                XLog.e("Failed to hook SmsHandler", e)
            }
            XLog.i("SmsCode initialize completely")
        }
    }

    private fun printDeviceInfo() {
        XLog.i("Phone manufacturer: %s", Build.MANUFACTURER)
        XLog.i("Phone model: %s", Build.MODEL)
        XLog.i("Android version: %s", Build.VERSION.RELEASE)
        val xposedVersion = resolveXposedVersion()
        if (xposedVersion != null) {
            XLog.i("Xposed bridge version: %d", xposedVersion)
        } else {
            XLog.i("Xposed bridge version: unknown")
        }
        XLog.i("SmsCode version: %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }

    private fun resolveXposedVersion(): Int? {
        return runCatching {
            val method = XposedBridge::class.java.getMethod("getXposedVersion")
            (method.invoke(null) as? Number)?.toInt()
        }.getOrNull() ?: runCatching {
            val field = XposedBridge::class.java.getDeclaredField("XPOSED_BRIDGE_VERSION")
            field.isAccessible = true
            (field.get(null) as? Number)?.toInt()
        }.getOrNull()
    }

    private fun hookSmsHandler(classloader: ClassLoader) {
        hookConstructor(classloader)
        hookDispatchIntent(classloader)
    }

    private fun hookConstructor(classloader: ClassLoader) {
        // minSdkVersion 35: Only hook for Android 14+ / 15+
        hookConstructor34(classloader)
    }

    // Android 14+
    private fun hookConstructor34(classLoader: ClassLoader) {
        XLog.i("Hooking InboundSmsHandler constructor for android v34+")
        val smsHandlerClazz = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader)
        if (smsHandlerClazz != null) {
            XposedBridge.hookAllConstructors(smsHandlerClazz, ConstructorHook())
        }
    }

    private fun hookDispatchIntent(classloader: ClassLoader) {
        // minSdkVersion 35: Only hook for Android 10+ / 15+
        hookDispatchIntent29(classloader)
    }

    // Android 10+
    private fun hookDispatchIntent29(classLoader: ClassLoader) {
        XLog.d("Hooking dispatchIntent() for Android v29+")
        val inboundSmsHandlerClass = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader) ?: run {
            XLog.e("Class: %s cannot found", SMS_HANDLER_CLASS)
            return
        }

        val methods = inboundSmsHandlerClass.declaredMethods
        var exactMethod: Method? = null
        val dispatchIntentMethodName = "dispatchIntent"
        var receiverIndex = 0
        for (method in methods) {
            if (dispatchIntentMethodName == method.name) {
                exactMethod = method
                val parameterTypes = method.parameterTypes
                for (i in parameterTypes.indices) {
                    if (BroadcastReceiver::class.java.isAssignableFrom(parameterTypes[i])) {
                        receiverIndex = i
                    }
                }
                break
            }
        }

        exactMethod?.let {
            XposedWrapper.hookMethod(it, DispatchIntentHook(receiverIndex))
        } ?: run {
            XLog.e("Method %s for Class %s cannot found", dispatchIntentMethodName, SMS_HANDLER_CLASS)
        }
    }

    private inner class ConstructorHook : XC_MethodHook() {
        @Throws(Throwable::class)
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                afterConstructorHandler(param)
            } catch (e: Throwable) {
                XLog.e("Error occurred in constructor hook", e)
                throw e
            }
        }
    }

    private fun afterConstructorHandler(param: XC_MethodHook.MethodHookParam) {
        val context = param.args.getOrNull(1) as? Context ?: return
        if (mPhoneContext == null) {
            mPhoneContext = context
            try {
                mPluginContext = mPhoneContext?.createPackageContext(
                    SMSCODE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
                if (mPluginContext != null) {
                    initNotificationChannel()
                    registerCopyCodeReceiver()
                    mPluginContext?.let { ModuleActivationStore.markActivated(it) }
                } else {
                    XLog.e("Plugin context is null after creation attempt")
                }
            } catch (e: Exception) {
                XLog.e("Create plugin context failed: %s", e)
            }
        }
    }

    private fun initNotificationChannel() {
        val channelId = NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION
        val channelName = getPluginContext()?.getString(R.string.channel_name_smscode_notification) ?: ""
        mPhoneContext?.let {
            NotificationUtils.createNotificationChannel(
                it,
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH,
            )
            XLog.d("Init notification channel succeed")
        }
    }

    private fun registerCopyCodeReceiver() {
        val pluginContext = mPluginContext ?: return
        if (!PrefsReader.showCodeNotification(pluginContext)) return
        mPhoneContext?.let {
            CopyCodeReceiver.registerMe(it)
            XLog.d("Register copy code receiver")
        }
    }

    private inner class DispatchIntentHook(private val mReceiverIndex: Int) : XC_MethodHook() {
        @Throws(Throwable::class)
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                beforeDispatchIntentHandler(param, mReceiverIndex)
            } catch (e: Throwable) {
                XLog.e("Error occurred in dispatchIntent() hook, ", e)
                throw e
            }
        }
    }

    private fun beforeDispatchIntentHandler(param: XC_MethodHook.MethodHookParam, receiverIndex: Int) {
        val intent = param.args.getOrNull(0) as? Intent ?: return
        val action = intent.action

        if (BuildConfig.DEBUG) {
            XLog.d("SmsHandlerHook: Received intent action: $action")
            intent.extras?.let { bundle ->
                XLog.d("SmsHandlerHook: Extra keys = %s", bundle.keySet().joinToString(","))
            }
        }

        if (Telephony.Sms.Intents.SMS_DELIVER_ACTION != action) {
            return
        }
        val eventId = ensureEventId(intent)
        val pduCount = getPduCount(intent)
        XLog.w(
            "Diag SMS_DELIVER intercepted: event_id=%s action=%s, pduCount=%d, extras=%s",
            eventId,
            action,
            pduCount,
            intent.extras != null,
        )

        val pluginContext = getPluginContext()
        val phoneContext = mPhoneContext
        if (pluginContext == null || phoneContext == null) {
            XLog.e("Context is null, skip parsing. pluginContext: %s, phoneContext: %s", pluginContext, phoneContext)
            return
        }
        val smsMsg = runCatching { SmsMsg.fromIntent(intent) }.getOrNull()
        val blacklistResult = SmsBlacklistUtils.match(pluginContext, smsMsg?.sender, smsMsg?.body)
        if (blacklistResult.matched) {
            XLog.w(
                "Diag sms blacklist matched: event_id=%s type=%s, pattern=%s, delete=%s, block=%s",
                eventId,
                blacklistResult.matchType,
                blacklistResult.pattern,
                blacklistResult.actionDelete,
                blacklistResult.actionBlock,
            )
            if (blacklistResult.actionDelete && !blacklistResult.actionBlock && smsMsg != null) {
                scheduleBlacklistDelete(pluginContext, phoneContext, smsMsg)
            }
            if (blacklistResult.actionBlock) {
                XLog.w("Diag sms block reason=%s event_id=%s", BLOCK_REASON_BLACKLIST, eventId)
                param.args.getOrNull(receiverIndex)?.let { receiver ->
                    deleteRawTableAndSendMessage(
                        inboundSmsHandler = param.thisObject,
                        smsReceiver = receiver,
                        reason = BLOCK_REASON_BLACKLIST,
                        eventId = eventId,
                    )
                    param.result = null
                }
                return
            }
        }

        val parseResult = CodeWorker(pluginContext, phoneContext, intent, eventId).parse()
        if (parseResult == null) {
            XLog.w("Diag parse result is null: event_id=%s no code matched or parse failed", eventId)
        } else {
            XLog.w("Diag parse result: event_id=%s blockSms=%s", eventId, parseResult.isBlockSms)
        }
        if (parseResult != null) {
            if (parseResult.isBlockSms) {
                XLog.w("Diag sms block reason=%s event_id=%s", BLOCK_REASON_PREF_BLOCK, eventId)
                param.args.getOrNull(receiverIndex)?.let { receiver ->
                    deleteRawTableAndSendMessage(
                        inboundSmsHandler = param.thisObject,
                        smsReceiver = receiver,
                        reason = BLOCK_REASON_PREF_BLOCK,
                        eventId = eventId,
                    )
                    param.result = null
                }
            } else {
                XLog.w(
                    "Diag allow system inbox persist: event_id=%s sender_hash=%s body_len=%d",
                    eventId,
                    senderHash(smsMsg?.sender),
                    smsMsg?.body?.length ?: 0,
                )
            }
        }
    }

    private fun getPduCount(intent: Intent): Int {
        return try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)?.size ?: -1
        } catch (t: Throwable) {
            XLog.w("Diag getPduCount failed: %s", t.message ?: "unknown")
            -1
        }
    }

    private fun scheduleBlacklistDelete(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) {
        SMS_OPERATION_EXECUTOR.execute {
            runCatching {
                OperateSmsAction(
                    pluginContext,
                    phoneContext,
                    smsMsg,
                    OperateSmsAction.FORCE_DELETE,
                ).call()
            }.onFailure {
                XLog.w("Diag sms blacklist delete task failed: %s", it.message ?: "unknown")
            }
        }
    }

    private fun deleteRawTableAndSendMessage(
        inboundSmsHandler: Any,
        smsReceiver: Any,
        reason: String,
        eventId: String,
    ) {
        XLog.w("Diag raw-table delete start: reason=%s event_id=%s", reason, eventId)
        val token = Binder.clearCallingIdentity()
        try {
            deleteFromRawTable(inboundSmsHandler, smsReceiver, reason, eventId)
        } catch (e: Throwable) {
            XLog.e("Error occurs when delete SMS data from raw table", e)
        } finally {
            Binder.restoreCallingIdentity(token)
        }

        sendEventBroadcastComplete(inboundSmsHandler, reason, eventId)
    }

    private fun sendEventBroadcastComplete(inboundSmsHandler: Any, reason: String, eventId: String) {
        XLog.d("Send event(EVENT_BROADCAST_COMPLETE): reason=%s event_id=%s", reason, eventId)
        XposedHelpers.callMethod(inboundSmsHandler, "sendMessage", EVENT_BROADCAST_COMPLETE)
    }

    @Throws(ReflectiveOperationException::class)
    private fun deleteFromRawTable(inboundSmsHandler: Any, smsReceiver: Any, reason: String, eventId: String) {
        // minSdkVersion 35: Always use Android 24+ method
        deleteFromRawTable24(inboundSmsHandler, smsReceiver, reason, eventId)
    }

    @Throws(ReflectiveOperationException::class)
    private fun deleteFromRawTable24(inboundSmsHandler: Any, smsReceiver: Any, reason: String, eventId: String) {
        XLog.d("Delete raw SMS data from database on Android 24+: reason=%s event_id=%s", reason, eventId)
        val deleteWhere = XposedHelpers.getObjectField(smsReceiver, "mDeleteWhere")
        val deleteWhereArgs = XposedHelpers.getObjectField(smsReceiver, "mDeleteWhereArgs")
        val markDeleted = 2

        callDeclaredMethod(
            SMS_HANDLER_CLASS,
            inboundSmsHandler,
            "deleteFromRawTable",
            deleteWhere,
            deleteWhereArgs,
            markDeleted,
        )
    }

    private fun ensureEventId(intent: Intent): String {
        val existing = intent.getStringExtra(EVENT_ID_EXTRA).orEmpty().trim()
        if (existing.isNotEmpty()) {
            return existing
        }
        val generated = "sms_${System.currentTimeMillis().toString(36)}_${abs(intent.hashCode()).toString(36)}"
        intent.putExtra(EVENT_ID_EXTRA, generated)
        return generated
    }

    private fun senderHash(sender: String?): String {
        val value = sender.orEmpty()
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode())
    }

    private fun getPluginContext(): Context? {
        if (mPluginContext == null) {
            try {
                mPluginContext = mPhoneContext?.createPackageContext(
                    SMSCODE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
            } catch (e: Exception) {
                XLog.e("Create plugin context failed: %s", e)
            }
        }
        return mPluginContext
    }

    companion object {
        const val ANDROID_PHONE_PACKAGE = "com.android.phone"
        private const val TELEPHONY_PACKAGE = "com.android.internal.telephony"
        private const val SMS_HANDLER_CLASS = "$TELEPHONY_PACKAGE.InboundSmsHandler"
        private val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private const val EVENT_BROADCAST_COMPLETE = 3
        private const val EVENT_ID_EXTRA = "event_id"
        private const val BLOCK_REASON_BLACKLIST = "blacklist_block"
        private const val BLOCK_REASON_PREF_BLOCK = "pref_block_sms"
        private val SMS_OPERATION_EXECUTOR = Executors.newSingleThreadExecutor()

        @Throws(InvocationTargetException::class, IllegalAccessException::class)
        private fun callDeclaredMethod(className: String, obj: Any, methodName: String, vararg args: Any?): Any? {
            val clz = XposedHelpers.findClass(className, obj.javaClass.classLoader)
            val method = XposedHelpers.findMethodBestMatch(clz, methodName, *args)
            return method.invoke(obj, *args)
        }
    }
}

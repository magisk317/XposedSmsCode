package com.github.magisk317.smscode.xp.hook.code

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.common.utils.ActivationDiagnosticsStore
import io.github.magisk317.smscode.xposed.utils.ModuleActivationStore
import com.github.magisk317.smscode.runtime.RuntimeNotificationFacade as NotificationUtils
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import com.github.magisk317.smscode.common.utils.SmsBlacklistUtils
import io.github.magisk317.smscode.xposed.utils.XLog
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import com.github.magisk317.smscode.xp.helper.RelayConflictNoticeHelper
import io.github.magisk317.smscode.verification.SmsDispatchChainBlockDeduplicator
import io.github.magisk317.smscode.verification.SmsIntentHookSupport as VerificationSmsIntentHookSupport
import io.github.magisk317.smscode.xposed.helper.XposedWrapper
import io.github.magisk317.smscode.xposed.hook.BaseHook
import io.github.magisk317.smscode.xposed.hook.telephony.InboundSmsBlocker
import com.github.magisk317.smscode.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.smscode.runtime.common.sim.SmsRoutingIntentExtras
import io.github.magisk317.smscode.xposed.hookapi.HookEnv
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.HookBridge
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.Method
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.Executors

/**
 * Hook class com.android.internal.telephony.InboundSmsHandler
 */
class SmsHandlerHook : BaseHook() {

    private var mPhoneContext: Context? = null
    private var mPluginContext: Context? = null
    private var smsInboxObserver: SmsInboxObserver? = null
    private val inboundSmsBlocker = InboundSmsBlocker(SMS_HANDLER_CLASS)
    private val constructorInitializer = SmsHookConstructorInitializer(
        runtimeInitializer = ::initializeRuntime,
        notificationChannelInitializer = { initNotificationChannel() },
        copyCodeRegistrar = { registerCopyCodeReceiver() },
        heartbeatRecorder = ::recordHookHeartbeat,
        suppressionLogger = ::logSuppressedOnce,
        inboxObserverRegistrar = ::registerSmsInboxObserver,
    )
    private val dispatchIntentHandler = SmsDispatchIntentHandler(
        runtimeResolver = ::recordHeartbeat,
        suppressionLogger = ::logSuppressedOnce,
        blacklistDeleteScheduler = ::scheduleBlacklistDelete,
        inboundBlocker = { inboundSmsHandler, receiver, reason, eventId ->
            inboundSmsBlocker.blockInboundSms(
                inboundSmsHandler = inboundSmsHandler,
                smsReceiver = receiver,
                reason = reason,
                eventId = eventId,
            )
        },
    )
    @Volatile
    private var suppressionLogged = false

    override fun onLoadPackage(lpparam: LoadParam) {
        XLog.withRoute(LogRoute.SMS_HOOK) {
            onLoadPackageRouted(lpparam)
        }
    }

    private fun onLoadPackageRouted(lpparam: LoadParam) {
        if (isSmsHandlerPackage(lpparam.packageName)) {
            val classLoader = lpparam.classLoader ?: run {
                XLog.w(
                    "SmsHandlerHook skip: classLoader is null for pkg=%s process=%s",
                    lpparam.packageName,
                    lpparam.processName,
                )
                return
            }
            val sharedHookKey = buildSharedProcessKey(
                prefix = "hook_init",
                packageName = lpparam.packageName,
                processName = lpparam.processName,
            )
            val sharedHookAge = claimProcessPropertyWithinWindow(
                key = sharedHookKey,
                windowMs = SHARED_HOOK_INIT_WINDOW_MS,
            )
            if (sharedHookAge != null) {
                XLog.w(
                    "SmsHandlerHook shared init skip: pkg=%s process=%s pid=%d ageMs=%d",
                    lpparam.packageName,
                    lpparam.processName,
                    android.os.Process.myPid(),
                    sharedHookAge,
                )
                return
            }
            val hookKey = buildHookInstallKey(lpparam)
            if (!markHookInstalled(hookKey)) {
                XLog.w(
                    "SmsHandlerHook already initialized, skip duplicate load: pkg=%s process=%s loader=%s",
                    lpparam.packageName,
                    lpparam.processName,
                    Integer.toHexString(System.identityHashCode(classLoader)),
                )
                return
            }
            XLog.i("SmsCode initializing in %s", lpparam.packageName)
            printDeviceInfo()
            try {
                hookSmsHandler(classLoader)
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
        return HookEnv.api.getXposedBridgeVersion() ?: HookEnv.api.getApiVersion()
    }

    private fun hookSmsHandler(classloader: ClassLoader) {
        hookConstructor(classloader)
        hookDispatchIntent(classloader)
        hookSmsDispatcherChain(classloader)
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
            HookBridge.hookAllConstructors(smsHandlerClazz, ConstructorHook())
        }
    }

    private fun hookDispatchIntent(classloader: ClassLoader) {
        // minSdkVersion 35: Only hook for Android 10+ / 15+
        hookDispatchIntent29(classloader)
    }

    private fun hookSmsDispatcherChain(classLoader: ClassLoader) {
        // Some ROMs/Android versions may dispatch SMS via alternative paths.
        hookDispatcherMethods(
            classLoader,
            SMS_HANDLER_CLASS,
            listOf(
                "dispatchSmsDeliveryIntent",
                "dispatchSmsDeliveryIntentToApp",
                "dispatchSmsDeliveryIntentToRegisteredReceivers",
            ),
        )
        hookDispatcherMethods(
            classLoader,
            "com.android.internal.telephony.SmsDispatchersController",
            listOf(
                "dispatchSmsDeliveryIntent",
                "dispatchSmsDeliveryIntentToApp",
                "dispatchSmsDeliveryIntentToRegisteredReceivers",
                "dispatchSmsDeliveryIntentToAppWithPermission",
            ),
        )
    }

    private fun hookDispatcherMethods(
        classLoader: ClassLoader,
        className: String,
        methodNames: List<String>,
    ) {
        val clazz = XposedWrapper.findClass(className, classLoader) ?: return
        methodNames.forEach { name ->
            val methods = clazz.declaredMethods.filter { it.name == name }
            if (methods.isEmpty()) return@forEach
            methods.forEach { method ->
                XposedWrapper.hookMethod(
                    method,
                    object : MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            XLog.withRoute(LogRoute.SMS_HOOK) {
                                val intent = extractOrBuildSmsIntent(
                                    param.args,
                                    fallbackAction = Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                                )
                                val action = intent?.action ?: extractIntentAction(param.args)
                                val pluginContext = getPluginContext()
                                if (isVerboseDiagEnabled(pluginContext)) {
                                    XLog.d(
                                        "Diag SMS dispatch chain: class=%s owner=%s method=%s action=%s args=%d",
                                        className,
                                        param.thisObject?.javaClass?.name ?: className,
                                        name,
                                        action ?: "<none>",
                                        param.args.size,
                                    )
                                }
                                if (className == SMS_HANDLER_CLASS) {
                                    maybeBlockFromDispatchChain(name, param, intent)
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    private fun extractIntentAction(args: Array<Any?>?): String? {
        if (args == null) return null
        for (arg in args) {
            if (arg is Intent) {
                return arg.action
            }
        }
        return null
    }

    // Android 10+
    private fun hookDispatchIntent29(classLoader: ClassLoader) {
        XLog.d("Hooking dispatchIntent() for Android v29+")
        val inboundSmsHandlerClass = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader) ?: run {
            XLog.e("Class: %s cannot found", SMS_HANDLER_CLASS)
            return
        }

        val dispatchIntentMethodName = "dispatchIntent"
        val methods = inboundSmsHandlerClass.declaredMethods.filter { it.name == dispatchIntentMethodName }
        if (methods.isEmpty()) {
            XLog.e("Method %s for Class %s cannot found", dispatchIntentMethodName, SMS_HANDLER_CLASS)
            return
        }
        methods.forEach { method ->
            var receiverIndex = -1
            method.parameterTypes.forEachIndexed { index, clazz ->
                if (receiverIndex < 0 && BroadcastReceiver::class.java.isAssignableFrom(clazz)) {
                    receiverIndex = index
                }
            }
            XposedWrapper.hookMethod(method, DispatchIntentHook(receiverIndex))
        }
    }

    private inner class ConstructorHook : MethodHook() {
        @Throws(Throwable::class)
        override fun afterHookedMethod(param: MethodHookParam) {
            XLog.withRoute(LogRoute.SMS_HOOK) {
                try {
                    afterConstructorHandler(param)
                } catch (e: Throwable) {
                    XLog.e("Error occurred in constructor hook", e)
                    throw e
                }
            }
        }
    }

    private fun afterConstructorHandler(param: MethodHookParam) {
        val context = param.args.getOrNull(1) as? Context ?: return
        constructorInitializer.handle(context)
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

    private fun registerSmsInboxObserver(runtime: SmsHookRuntimeContext) {
        val pluginContext = runtime.pluginContext
        val phoneContext = runtime.phoneContext
        if (smsInboxObserver != null) return
        val observerKey = buildSharedProcessKey(
            prefix = "sms_observer",
            packageName = phoneContext.packageName,
            processName = phoneContext.applicationInfo?.processName ?: phoneContext.packageName,
        )
        val observerAge = claimProcessPropertyWithinWindow(
            key = observerKey,
            windowMs = SHARED_OBSERVER_WINDOW_MS,
        )
        if (observerAge != null) {
            XLog.w(
                "SmsInboxObserver shared register skip: key=%s pid=%d ageMs=%d",
                observerKey,
                android.os.Process.myPid(),
                observerAge,
            )
            return
        }
        smsInboxObserver = SmsInboxObserver(pluginContext, phoneContext).also { it.register() }
    }

    private fun initializeRuntime(phoneContext: Context): SmsHookRuntimeContext? {
        if (mPhoneContext == null) {
            mPhoneContext = phoneContext
        }
        val pluginContext = getPluginContext() ?: return null
        return SmsHookRuntimeContext(
            phoneContext = mPhoneContext ?: phoneContext,
            pluginContext = pluginContext,
        )
    }

    private fun currentRuntime(): SmsHookRuntimeContext? {
        val phoneContext = mPhoneContext ?: return null
        val pluginContext = getPluginContext() ?: return null
        return SmsHookRuntimeContext(
            phoneContext = phoneContext,
            pluginContext = pluginContext,
        )
    }

    private fun recordHeartbeat(source: String): SmsHookRuntimeContext? {
        val runtime = currentRuntime() ?: return null
        recordHookHeartbeat(source)
        return runtime
    }

    private fun recordHookHeartbeat(source: String) {
        val runtime = currentRuntime() ?: return
        ActivationDiagnosticsStore.recordHookHeartbeat(
            context = runtime.pluginContext,
            packageName = runtime.phoneContext.packageName,
            processName = runtime.phoneContext.applicationInfo?.processName ?: runtime.phoneContext.packageName,
            source = source,
            verboseLogging = PrefsReader.isVerboseLogMode(runtime.pluginContext),
        )
    }

    private inner class DispatchIntentHook(private val mReceiverIndex: Int) : MethodHook() {
        @Throws(Throwable::class)
        override fun beforeHookedMethod(param: MethodHookParam) {
            XLog.withRoute(LogRoute.SMS_HOOK) {
                try {
                    beforeDispatchIntentHandler(param, mReceiverIndex)
                } catch (e: Throwable) {
                    XLog.e("Error occurred in dispatchIntent() hook, ", e)
                }
            }
        }
    }

    private fun beforeDispatchIntentHandler(param: MethodHookParam, receiverIndex: Int) {
        val intent = param.args.getOrNull(0) as? Intent ?: return
        val action = intent.action

        if (BuildConfig.DEBUG) {
            XLog.d("SmsHandlerHook: Received intent action: $action")
            intent.extras?.let { bundle ->
                XLog.d("SmsHandlerHook: Extra keys = %s", bundle.keySet().joinToString(","))
            }
        }

        if (!VerificationSmsIntentHookSupport.isSmsAction(action)) {
            return
        }
        val eventId = VerificationSmsIntentHookSupport.ensureEventId(intent)
        if (VerificationSmsIntentHookSupport.markDispatchHandled(intent, action)) {
            XLog.d(
                "Diag SMS dispatch duplicate skip: event_id=%s action=%s source=intent_extra",
                eventId,
                action,
            )
            return
        }
        val pluginContext = getPluginContext()
        val phoneContext = mPhoneContext
        if (pluginContext == null || phoneContext == null) {
            XLog.e("Context is null, skip parsing. pluginContext: %s, phoneContext: %s", pluginContext, phoneContext)
            return
        }
        if (shouldSkipDispatchBySharedDedup(pluginContext, eventId, action)) {
            return
        }
        val pduCount = getPduCount(intent)
        val routing = SmsRoutingIntentExtras.readFrom(intent)
        XLog.i(
            "Diag SMS intent intercepted: event_id=%s action=%s pduCount=%d extras=%s simSlot=%d subId=%d",
            eventId,
            action,
            pduCount,
            intent.extras != null,
            routing.simSlot ?: -1,
            routing.subId ?: 0,
        )
        val outcome = dispatchIntentHandler.handle(
            intent = intent,
            eventId = eventId,
            inboundSmsHandler = param.thisObject,
            receiver = param.args.getOrNull(receiverIndex),
        )
        if (outcome.inboundBlocked) {
            param.result = null
        }
        if (outcome.shouldStopDispatch) {
            return
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
            XLog.withRoute(LogRoute.SMS_HOOK) {
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
    }

    private fun shouldSkipDispatchBySharedDedup(
        pluginContext: Context,
        eventId: String,
        action: String?,
    ): Boolean {
        if (eventId.isBlank() || action.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val key = "$eventId|$action"
        return runCatching {
            val file = File(StorageUtils.getExternalFilesDir(pluginContext), DISPATCH_DEDUP_FILE_NAME)
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { raf ->
                raf.channel.use { channel ->
                    channel.lock().use {
                        val entries = readDispatchDedupEntries(raf)
                        val iterator = entries.entries.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (now - entry.value > DISPATCH_DEDUP_WINDOW_MS) {
                                iterator.remove()
                            }
                        }
                        val last = entries[key]
                        if (last != null && now - last <= DISPATCH_DEDUP_WINDOW_MS) {
                            XLog.d(
                                "Diag SMS dispatch duplicate skip: event_id=%s action=%s source=shared_store ageMs=%d",
                                eventId,
                                action,
                                now - last,
                            )
                            writeDispatchDedupEntries(raf, entries)
                            return true
                        }
                        entries[key] = now
                        while (entries.size > MAX_DISPATCH_DEDUP_ENTRIES) {
                            val firstKey = entries.entries.firstOrNull()?.key ?: break
                            entries.remove(firstKey)
                        }
                        writeDispatchDedupEntries(raf, entries)
                        false
                    }
                }
            }
        }.onFailure {
            XLog.w(
                "Diag SMS dispatch shared dedup failed: event_id=%s action=%s err=%s",
                eventId,
                action,
                it.message ?: it.javaClass.simpleName,
            )
        }.getOrDefault(false)
    }

    private fun readDispatchDedupEntries(raf: RandomAccessFile): LinkedHashMap<String, Long> {
        val entries = LinkedHashMap<String, Long>()
        raf.seek(0L)
        while (true) {
            val rawLine = raf.readLine() ?: break
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val split = line.indexOf('=')
            if (split <= 0) continue
            val key = line.substring(0, split)
            val value = line.substring(split + 1).toLongOrNull() ?: continue
            entries[key] = value
        }
        return entries
    }

    private fun writeDispatchDedupEntries(
        raf: RandomAccessFile,
        entries: LinkedHashMap<String, Long>,
    ) {
        raf.setLength(0L)
        raf.seek(0L)
        val content = buildString {
            entries.forEach { (key, value) ->
                append(key)
                append('=')
                append(value)
                append('\n')
            }
        }
        raf.write(content.toByteArray())
    }

    private fun senderHash(sender: String?): String {
        val value = sender.orEmpty()
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode())
    }

    @Suppress("ReturnCount")
    private fun maybeBlockFromDispatchChain(
        methodName: String,
        param: MethodHookParam,
        smsIntent: Intent?,
    ) {
        val intent = smsIntent ?: return
        val action = intent.action
        if (!VerificationSmsIntentHookSupport.isSmsAction(action)) return
        val pluginContext = getPluginContext() ?: return
        val phoneContext = mPhoneContext ?: return
        if (ModuleConflictArbiter.shouldSuppressByRelay(phoneContext, "SmsHandlerHook#$methodName")) {
            logSuppressedOnce("dispatchChain:$methodName")
            return
        }
        val eventId = VerificationSmsIntentHookSupport.ensureEventId(intent)
        val evaluation = SmsBlockEvaluator.evaluate(pluginContext, intent, eventId, "dispatch_chain") ?: return
        if (evaluation.blacklistDeleteOnly && evaluation.smsMsg != null) {
            scheduleBlacklistDelete(pluginContext, phoneContext, evaluation.smsMsg)
        }
        val reason = evaluation.blockReason ?: return
        if (shouldSkipDispatchChainBlock(evaluation.smsMsg, action, reason)) {
            return
        }
        ActivationDiagnosticsStore.recordHookHeartbeat(
            context = pluginContext,
            packageName = phoneContext.packageName,
            processName = phoneContext.applicationInfo?.processName ?: phoneContext.packageName,
            source = "sms_handler_dispatch_chain",
            verboseLogging = PrefsReader.isVerboseLogMode(pluginContext),
        )
        XLog.w(
            "Diag SMS dispatch chain block: method=%s reason=%s event_id=%s",
            methodName,
            reason,
            eventId,
        )
        CodeWorker(pluginContext, phoneContext, intent, eventId).parse()
        val inbound = param.thisObject ?: return
        val smsReceiver = findRawTableReceiver(param.args)
        if (smsReceiver != null) {
            val blocked = inboundSmsBlocker.blockInboundSms(
                inboundSmsHandler = inbound,
                smsReceiver = smsReceiver,
                reason = reason,
                eventId = eventId,
            )
            if (!blocked) {
                XLog.w(
                    "Diag dispatch chain block aborted: cleanup incomplete method=%s event_id=%s",
                    methodName,
                    eventId,
                )
                return
            }
        } else {
            XLog.w(
                "Diag dispatch chain block fallback: receiver unavailable method=%s event_id=%s",
                methodName,
                eventId,
            )
            return
        }
        param.result = defaultResultForType((param.method as? Method)?.returnType)
    }

    private fun extractOrBuildSmsIntent(args: Array<Any?>?, fallbackAction: String): Intent? {
        if (args == null) return null
        args.forEach { arg ->
            if (arg is Intent) {
                return arg
            }
        }
        val pduList = args.firstNotNullOfOrNull { arg ->
            val array = arg as? Array<*> ?: return@firstNotNullOfOrNull null
            val pdus = array.mapNotNull { it as? ByteArray }
            if (pdus.isEmpty() || pdus.size != array.size) null else pdus
        } ?: return null
        val format = args.firstNotNullOfOrNull { arg ->
            val text = arg as? String ?: return@firstNotNullOfOrNull null
            if (text.equals("3gpp", ignoreCase = true) || text.equals("3gpp2", ignoreCase = true)) {
                text
            } else {
                null
            }
        }
        return Intent(fallbackAction).apply {
            putExtra("pdus", pduList.toTypedArray())
            if (!format.isNullOrBlank()) {
                putExtra("format", format)
            }
        }
    }

    private fun findRawTableReceiver(args: Array<Any?>?): Any? {
        if (args == null) return null
        return args.firstOrNull { candidate ->
            candidate != null &&
                hasField(candidate, "mDeleteWhere") &&
                hasField(candidate, "mDeleteWhereArgs")
        }
    }

    private fun hasField(instance: Any, fieldName: String): Boolean {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            if (current.declaredFields.any { it.name == fieldName }) {
                return true
            }
            current = current.superclass
        }
        return false
    }

    private fun shouldSkipDispatchChainBlock(
        smsMsg: SmsMsg?,
        action: String?,
        reason: String,
    ): Boolean {
        val result = dispatchChainBlockDeduplicator.shouldSkip(
            smsMsg = smsMsg?.toVerificationMessage(),
            action = action,
            reason = reason,
        )
        if (result.shouldSkip) {
            XLog.d(
                "Diag dispatch chain block duplicate skip: action=%s reason=%s ageMs=%d",
                action,
                reason,
                result.ageMs ?: 0L,
            )
            return true
        }
        return false
    }

    private fun defaultResultForType(type: Class<*>?): Any? {
        return when (type) {
            null, Void.TYPE, Void::class.java -> null
            Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> false
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> 0
            Long::class.javaPrimitiveType, Long::class.javaObjectType -> 0L
            Float::class.javaPrimitiveType, Float::class.javaObjectType -> 0f
            Double::class.javaPrimitiveType, Double::class.javaObjectType -> 0.0
            Short::class.javaPrimitiveType, Short::class.javaObjectType -> 0.toShort()
            Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 0.toByte()
            Char::class.javaPrimitiveType, Char::class.javaObjectType -> 0.toChar()
            else -> null
        }
    }

    private fun logSuppressedOnce(stage: String) {
        if (suppressionLogged) return
        synchronized(this) {
            if (suppressionLogged) return
            XLog.w(
                "SmsHandlerHook suppressed: reason=%s stage=%s package=%s",
                ModuleConflictArbiter.SUPPRESSION_REASON,
                stage,
                ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            )
            suppressionLogged = true
        }
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

    private fun isVerboseDiagEnabled(context: Context?): Boolean {
        if (context == null) return false
        return runCatching { PrefsReader.isVerboseLogMode(context) }.getOrDefault(false)
    }

    companion object {
        const val ANDROID_PHONE_PACKAGE = "com.android.phone"
        const val XIAOMI_PHONE_PACKAGE = "com.xiaomi.phone"
        private const val TELEPHONY_PACKAGE = "com.android.internal.telephony"
        private const val SMS_HANDLER_CLASS = "$TELEPHONY_PACKAGE.InboundSmsHandler"
        private val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private const val DISPATCH_DEDUP_FILE_NAME = "dispatch_dedup"
        private const val DISPATCH_DEDUP_WINDOW_MS = 8_000L
        private const val MAX_DISPATCH_DEDUP_ENTRIES = 256
        private val SMS_OPERATION_EXECUTOR = Executors.newSingleThreadExecutor()
        private val installedHookKeys = Collections.synchronizedSet(mutableSetOf<String>())
        private val dispatchChainBlockDeduplicator = SmsDispatchChainBlockDeduplicator()

        fun isSmsHandlerPackage(packageName: String): Boolean {
            return packageName == ANDROID_PHONE_PACKAGE || packageName == XIAOMI_PHONE_PACKAGE
        }

        private fun buildHookInstallKey(lpparam: LoadParam): String {
            return buildString {
                append(lpparam.packageName)
                append('|')
                append(lpparam.processName.ifBlank { lpparam.packageName })
            }
        }

        private fun buildSharedProcessKey(
            prefix: String,
            packageName: String,
            processName: String,
        ): String {
            return buildString {
                append(prefix)
                append('|')
                append(packageName)
                append('|')
                append(processName.ifBlank { packageName })
                append("|pid:")
                append(android.os.Process.myPid())
            }
        }

        private fun claimProcessPropertyWithinWindow(
            key: String,
            windowMs: Long,
        ): Long? = synchronized(PROCESS_PROPERTY_LOCK) {
            val now = System.currentTimeMillis()
            val raw = System.getProperty(key)
            val last = raw?.toLongOrNull()
            if (last != null && now - last <= windowMs) {
                return@synchronized now - last
            }
            System.setProperty(key, now.toString())
            null
        }

        private fun markHookInstalled(key: String): Boolean = installedHookKeys.add(key)

        private const val SHARED_HOOK_INIT_WINDOW_MS = 5 * 60 * 1000L
        private const val SHARED_OBSERVER_WINDOW_MS = 5 * 60 * 1000L
        private val PROCESS_PROPERTY_LOCK = Any()
    }
}

package com.github.magisk317.smscode.xp.hook.mms

import android.content.Context
import android.content.Intent
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.common.utils.ActivationDiagnosticsStore
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import com.github.magisk317.smscode.xp.hook.code.CodeWorker
import com.github.magisk317.smscode.xp.hook.code.SmsBlockEvaluator
import io.github.magisk317.smscode.verification.SmsIntentHookSupport
import com.github.magisk317.smscode.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.smscode.xposed.helper.XposedWrapper
import io.github.magisk317.smscode.xposed.hook.BaseHook
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.xposed.utils.XLog
import java.lang.reflect.Method
import java.util.concurrent.Executors

class MmsMessagesHook : BaseHook() {
    override fun onLoadPackage(lpparam: LoadParam) {
        XLog.withRoute(LogRoute.SMS_HOOK) {
            onLoadPackageRouted(lpparam)
        }
    }

    private fun onLoadPackageRouted(lpparam: LoadParam) {
        if (lpparam.packageName != MMS_PACKAGE_NAME) return
        val classLoader = lpparam.classLoader ?: run {
            XLog.w(
                "MmsMessagesHook skip: classLoader is null for pkg=%s process=%s",
                lpparam.packageName,
                lpparam.processName,
            )
            return
        }
        XLog.i("MmsMessagesHook initializing")
        var totalHooks = 0
        RECEIVER_CLASS_NAMES.forEach { totalHooks += hookReceiver(classLoader, it) }
        SERVICE_CLASS_NAMES.forEach { totalHooks += hookIntentMethods(classLoader, it) }
        if (totalHooks == 0) {
            XLog.w("MmsMessagesHook found no usable receiver/service entrypoints in %s", MMS_PACKAGE_NAME)
        } else {
            XLog.i("MmsMessagesHook installed methods=%d", totalHooks)
        }
    }

    private fun hookReceiver(classLoader: ClassLoader, receiverClassName: String): Int {
        val receiverClass = XposedWrapper.findClass(receiverClassName, classLoader)
        if (receiverClass == null) {
            XLog.w("MmsMessagesHook receiver class missing: %s", receiverClassName)
            return 0
        }
        val callback = object : MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                XLog.withRoute(LogRoute.SMS_HOOK) {
                    val context = param.args.getOrNull(0) as? Context ?: return@withRoute
                    val intent = param.args.getOrNull(1) as? Intent ?: return@withRoute
                    maybeBlock(context, intent, receiverClassName, param)
                }
            }
        }

        val visited = linkedSetOf<Class<*>>()
        var current: Class<*>? = receiverClass
        var hookedCount = 0
        while (current != null && current != Any::class.java && visited.add(current)) {
            current.declaredMethods.forEach { method ->
                val types = method.parameterTypes
                if (types.size == 2 &&
                    Context::class.java.isAssignableFrom(types[0]) &&
                    Intent::class.java.isAssignableFrom(types[1])
                ) {
                    XposedWrapper.hookMethod(method, callback)
                    hookedCount += 1
                }
            }
            current = current.superclass
        }
        XLog.i("MmsMessagesHook hooked receiver: %s methods=%d", receiverClassName, hookedCount)
        return hookedCount
    }

    private fun hookIntentMethods(classLoader: ClassLoader, className: String): Int {
        val clazz = XposedWrapper.findClass(className, classLoader)
        if (clazz == null) {
            XLog.w("MmsMessagesHook service class missing: %s", className)
            return 0
        }
        val methodNames = setOf("onStartCommand", "handleIntent", "onHandleIntent")
        var hookedCount = 0
        clazz.declaredMethods
            .filter { it.name in methodNames && it.parameterTypes.any(Intent::class.java::isAssignableFrom) }
            .forEach { method ->
                XposedWrapper.hookMethod(
                    method,
                    object : MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            XLog.withRoute(LogRoute.SMS_HOOK) {
                                val context = param.thisObject as? Context ?: return@withRoute
                                val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return@withRoute
                                maybeBlock(context, intent, "${className}.${method.name}", param)
                            }
                        }
                    },
                )
                hookedCount += 1
            }
        XLog.i("MmsMessagesHook hooked service: %s methods=%d", className, hookedCount)
        return hookedCount
    }

    private fun maybeBlock(
        context: Context,
        intent: Intent,
        source: String,
        param: MethodHookParam,
    ) {
        val action = intent.action
        if (!SmsIntentHookSupport.isSmsAction(action)) return
        val eventId = SmsIntentHookSupport.ensureEventId(intent)
        val pluginContext = runCatching {
            context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull()
        val verboseDiag = pluginContext != null && PrefsReader.isVerboseLogMode(pluginContext)
        if (verboseDiag) {
            XLog.w(
                "Diag MMS SMS entry: source=%s action=%s event_id=%s owner=%s",
                source,
                action,
                eventId,
                param.thisObject?.javaClass?.name ?: "<none>",
            )
        }
        if (SmsIntentHookSupport.markDispatchHandled(intent, action)) {
            XLog.w("MmsMessagesHook duplicate skip: source=%s event_id=%s action=%s", source, eventId, action)
            return
        }
        if (ModuleConflictArbiter.shouldSuppressByRelay(context, "MmsMessagesHook#$source")) {
            XLog.w(
                "MmsMessagesHook suppressed: source=%s reason=%s event_id=%s",
                source,
                ModuleConflictArbiter.SUPPRESSION_REASON,
                eventId,
            )
            return
        }
        val resolvedPluginContext = pluginContext ?: return
        ActivationDiagnosticsStore.recordHookHeartbeat(
            context = resolvedPluginContext,
            packageName = MMS_PACKAGE_NAME,
            processName = context.applicationInfo?.processName ?: MMS_PACKAGE_NAME,
            source = "mms_${source.substringAfterLast('.')}",
            verboseLogging = PrefsReader.isVerboseLogMode(resolvedPluginContext),
        )
        val evaluation = SmsBlockEvaluator.evaluate(resolvedPluginContext, intent, eventId, "mms") ?: return
        if (evaluation.blacklistDeleteOnly && evaluation.smsMsg != null) {
            scheduleBlacklistDelete(resolvedPluginContext, context, evaluation.smsMsg)
        }
        XLog.i("MmsMessagesHook parse start: source=%s event_id=%s", source, eventId)
        CodeWorker(resolvedPluginContext, context, intent, eventId).parse()
        val reason = evaluation.blockReason ?: run {
            XLog.i("MmsMessagesHook allow system delivery after parse: source=%s event_id=%s", source, eventId)
            return
        }
        XLog.w("MmsMessagesHook block start: source=%s reason=%s event_id=%s", source, reason, eventId)
        param.result = defaultResultForType((param.method as? Method)?.returnType)
    }

    private fun scheduleBlacklistDelete(pluginContext: Context, hostContext: Context, smsMsg: SmsMsg) {
        SMS_OPERATION_EXECUTOR.execute {
            XLog.withRoute(LogRoute.SMS_HOOK) {
                runCatching {
                    OperateSmsAction(
                        pluginContext,
                        hostContext,
                        smsMsg,
                        OperateSmsAction.FORCE_DELETE,
                    ).call()
                }.onFailure {
                    XLog.w("MmsMessagesHook blacklist delete failed: %s", it.message ?: "unknown")
                }
            }
        }
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

    companion object {
        private const val MMS_PACKAGE_NAME = "com.android.mms"
        private val RECEIVER_CLASS_NAMES = listOf(
            "com.android.mms.transaction.PrivilegedSmsReceiver",
            "com.android.mms.transaction.SmsReceiver",
        )
        private val SERVICE_CLASS_NAMES = listOf(
            "com.android.mms.transaction.SmsReceiverService",
        )
        private val SMS_OPERATION_EXECUTOR = Executors.newSingleThreadExecutor()
    }
}

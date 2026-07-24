package com.github.magisk317.smscode.xp.hook.mms

import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import com.github.magisk317.smscode.xp.helper.RelayConflictNoticeHelper
import com.github.magisk317.smscode.xp.hook.code.CodeWorker
import com.github.magisk317.smscode.xp.hook.code.SmsBlockEvaluator
import io.github.magisk317.smscode.verification.SmsIntentHookSupport
import com.github.magisk317.smscode.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.xposed.logging.MagiskOtel
import java.lang.reflect.Method
import java.util.concurrent.Executors

class MmsMessagesHook : BaseHook() {
    override fun onLoadPackage(param: LoadParam) {
        XLog.withRoute(LogRoute.SMS_HOOK) {
            onLoadPackageRouted(param)
        }
    }

    private fun onLoadPackageRouted(param: LoadParam) {
        if (param.packageName != MMS_PACKAGE_NAME) return
        val classLoader = param.classLoader
        XLog.i("MmsMessagesHook initializing")
        var totalHooks = 0
        RECEIVER_CLASS_NAMES.forEach { totalHooks += hookReceiver(classLoader, it) }
        SERVICE_CLASS_NAMES.forEach { totalHooks += hookIntentMethods(classLoader, it) }
        if (totalHooks == 0) {
            XLog.w("MmsMessagesHook found no usable receiver/service entrypoints in %s", MMS_PACKAGE_NAME)
            MagiskOtel.event(
                name = "hook.load",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "mms_hook",
                    "reason" to "no_entrypoints",
                    "target_package" to MMS_PACKAGE_NAME,
                ),
                statusOk = true,
            )
        } else {
            XLog.i("MmsMessagesHook installed methods=%d", totalHooks)
            MagiskOtel.event(
                name = "hook.load",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "mms_hook",
                    "reason" to "installed",
                    "target_package" to MMS_PACKAGE_NAME,
                    "found_count" to totalHooks.toString(),
                ),
                statusOk = true,
            )
        }
    }

    private fun hookReceiver(classLoader: ClassLoader, receiverClassName: String): Int {
        val receiverClass = runCatching { HookHelpers.findClass(receiverClassName, classLoader) }.getOrNull()
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
                    HookEnv.api.hookMethod(method, callback)
                    hookedCount += 1
                }
            }
            current = current.superclass
        }
        XLog.i("MmsMessagesHook hooked receiver: %s methods=%d", receiverClassName, hookedCount)
        return hookedCount
    }

    private fun hookIntentMethods(classLoader: ClassLoader, className: String): Int {
        val clazz = runCatching { HookHelpers.findClass(className, classLoader) }.getOrNull()
        if (clazz == null) {
            XLog.w("MmsMessagesHook service class missing: %s", className)
            return 0
        }
        val methodNames = setOf("onStartCommand", "handleIntent", "onHandleIntent")
        var hookedCount = 0
        clazz.declaredMethods
            .filter { it.name in methodNames && it.parameterTypes.any(Intent::class.java::isAssignableFrom) }
            .forEach { method ->
                HookEnv.api.hookMethod(
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
        pluginContext?.let({ ctx -> HookRuntimeBridge.hookProcessInit?.invoke(ctx) })
        val verboseDiag = pluginContext != null && HookRuntimeBridge.prefsAccess.isVerboseLogMode(pluginContext)
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
            MagiskOtel.event(
                name = "sms.dispatch",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "mms",
                    "reason" to "duplicate",
                    "event_id_present" to eventId.isNotBlank().toString(),
                    "source" to source.substringAfterLast('.').take(48),
                ),
                statusOk = true,
            )
            return
        }
        if (ModuleConflictArbiter.shouldSuppressByRelay(context, "MmsMessagesHook#$source")) {
            XLog.w(
                "MmsMessagesHook suppressed: source=%s reason=%s event_id=%s",
                source,
                ModuleConflictArbiter.SUPPRESSION_REASON,
                eventId,
            )
            if (pluginContext != null) {
                RelayConflictNoticeHelper.notifyConflictOnSms(pluginContext, context, eventId)
            }
            MagiskOtel.event(
                name = "hook.conflict",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "mms",
                    "reason" to "suppressed_by_relay",
                    "event_id_present" to eventId.isNotBlank().toString(),
                ),
                statusOk = true,
            )
            return
        }
        val resolvedPluginContext = pluginContext ?: return
        ActivationDiagnosticsStore.recordHookHeartbeat(
            context = resolvedPluginContext,
            packageName = MMS_PACKAGE_NAME,
            processName = context.applicationInfo?.processName ?: MMS_PACKAGE_NAME,
            source = "mms_${source.substringAfterLast('.')}",
            verboseLogging = HookRuntimeBridge.prefsAccess.isVerboseLogMode(resolvedPluginContext),
        )
        val evaluation = SmsBlockEvaluator.evaluate(resolvedPluginContext, intent, eventId, "mms") ?: return
        if (evaluation.blacklistDeleteOnly && evaluation.smsMsg != null) {
            scheduleBlacklistDelete(resolvedPluginContext, context, evaluation.smsMsg)
        }
        XLog.i("MmsMessagesHook parse start: source=%s event_id=%s", source, eventId)
        CodeWorker(resolvedPluginContext, context, intent, eventId).parse()
        val reason = evaluation.blockReason ?: run {
            XLog.i("MmsMessagesHook allow system delivery after parse: source=%s event_id=%s", source, eventId)
            MagiskOtel.event(
                name = "sms.dispatch",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "mms",
                    "reason" to "allow_after_parse",
                    "event_id_present" to eventId.isNotBlank().toString(),
                    "source" to source.substringAfterLast('.').take(48),
                ),
                statusOk = true,
            )
            return
        }
        XLog.w("MmsMessagesHook block start: source=%s reason=%s event_id=%s", source, reason, eventId)
        MagiskOtel.event(
            name = "sms.block",
            attributes = mapOf(
                "result" to "skip",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "mms",
                "reason" to reason.take(64),
                "event_id_present" to eventId.isNotBlank().toString(),
                "source" to source.substringAfterLast('.').take(48),
            ),
            statusOk = true,
        )
        // Delete SMS from database so it doesn't appear in the inbox
        if (evaluation.smsMsg != null) {
            scheduleBlacklistDelete(resolvedPluginContext, context, evaluation.smsMsg)
        }
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

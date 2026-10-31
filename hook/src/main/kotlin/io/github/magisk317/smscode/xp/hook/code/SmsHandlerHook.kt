package io.github.magisk317.smscode.xp.hook.code

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import io.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.smscode.common.constant.NotificationConst
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.common.utils.SmsBlacklistUtils
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.xp.helper.SmsCodeConflictNoticeHelper
import io.github.magisk317.smscode.hook.R
import io.github.magisk317.smscode.runtime.verification.SmsDispatchChainBlockDeduplicator
import io.github.magisk317.xposed.capability.helper.SmsHookContextRecovery
import io.github.magisk317.smscode.runtime.verification.SmsIntentHookSupport as VerificationSmsIntentHookSupport
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.smscode.xposed.hook.telephony.InboundSmsBlocker
import io.github.magisk317.smscode.xposed.hook.telephony.SmsRoutingProbeResolver
import io.github.magisk317.smscode.xposed.hook.telephony.InboundSmsHookClaim
import io.github.magisk317.smscode.xposed.hook.telephony.InboundSmsHookInstaller
import io.github.magisk317.smscode.xp.hook.SmsHookRuntimeContext
import io.github.magisk317.smscode.xp.hook.SmsHookRuntimeSession
import io.github.magisk317.smscode.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.xposed.runtime.contract.sim.SmsRoutingIntentExtras
import io.github.magisk317.smscode.runtime.verification.PendingSmsReplayQueue
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.runtime.verification.SmsDispatchIntentDeduplicator
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Hook class com.android.internal.telephony.InboundSmsHandler
 */
class SmsHandlerHook : BaseHook() {

    private val runtimeSession = SmsHookRuntimeSession(SMSCODE_PACKAGE)
    private var smsInboxObserver: SmsInboxObserver? = null
    private val inboundSmsBlocker = InboundSmsBlocker(SMS_HANDLER_CLASS)
    private val smsOperationExecutor = Executors.newSingleThreadExecutor()
    private val constructorInitializer = createXscConstructorInitializer(
        runtimeInitializer = runtimeSession::initialize,
        notificationChannelInitializer = { runtime -> initNotificationChannel(runtime) },
        copyCodeRegistrar = { runtime -> registerCopyCodeReceiver(runtime) },
        heartbeatRecorder = { source -> runtimeSession.recordHeartbeat(source) },
        suppressionLogger = ::logSuppressedOnce,
        inboxObserverRegistrar = ::registerSmsInboxObserver,
    )
    private val pendingSmsReplay = PendingSmsReplayQueue()

    private val dispatchIntentHandler = SmsDispatchIntentHandler(
        runtimeResolver = runtimeSession::recordHeartbeat,
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

    override fun onHotReloading() {
        // Release resources owned by the old module ClassLoader before libxposed hot reload.
        smsInboxObserver?.unregister()
        smsInboxObserver = null
        smsOperationExecutor.shutdownNow()
        runtimeSession.currentOrResolve()?.let { CopyCodeReceiver.unregisterMe(it.phoneContext) }
    }


    override fun onLoadPackage(param: LoadParam) {
        XLog.withRoute(LogRoute.SMS_HOOK.id) {
            onLoadPackageRouted(param)
        }
    }

    private fun onLoadPackageRouted(param: LoadParam) {
        if (isSmsHandlerPackage(param.packageName)) {
            val classLoader = param.classLoader
            val sharedHookKey = buildSharedProcessKey(
                prefix = "hook_init",
                packageName = param.packageName,
                processName = param.processName,
            )
            val sharedHookAge = claimProcessPropertyWithinWindow(
                key = sharedHookKey,
                windowMs = SHARED_HOOK_INIT_WINDOW_MS,
            )
            if (sharedHookAge != null) {
                XLog.w(
                    message = "SmsHandlerHook shared init skip: pkg=%s process=%s pid=%d ageMs=%d",
                    param.packageName,
                    param.processName,
                    android.os.Process.myPid(),
                    sharedHookAge,
                )
                emitHandler(
                    result = "skip",
                    reason = "shared_init_window",
                    stage = "hook_install",
                )
                return
            }
            val hookKey = buildHookInstallKey(param)
            if (!markHookInstalled(hookKey)) {
                XLog.w(
                    message = "SmsHandlerHook already initialized, skip duplicate load: pkg=%s process=%s loader=%s",
                    param.packageName,
                    param.processName,
                    Integer.toHexString(System.identityHashCode(classLoader)),
                )
                emitHandler(
                    result = "skip",
                    reason = "already_initialized",
                    stage = "hook_install",
                )
                return
            }
            XLog.i(message = "SmsCode initializing in %s", param.packageName)
            printDeviceInfo()
            try {
                hookSmsHandler(classLoader)
                emitHandler(
                    result = "ok",
                    reason = "installed",
                    stage = "hook_install",
                )
            } catch (e: Throwable) {
                XLog.e(message = "Failed to hook SmsHandler", e)
                emitHandler(
                    result = "error",
                    reason = "install_failed",
                    stage = "hook_install",
                    statusOk = false,
                    errorClass = e.javaClass.simpleName,
                )
            }
            XLog.i(message = "SmsCode initialize completely")
        }
    }

    private fun printDeviceInfo() {
        XLog.i(message = "Phone manufacturer: %s", Build.MANUFACTURER)
        XLog.i(message = "Phone model: %s", Build.MODEL)
        XLog.i(message = "Android version: %s", Build.VERSION.RELEASE)
        val xposedVersion = resolveXposedVersion()
        if (xposedVersion != null) {
            XLog.i(message = "Xposed bridge version: %d", xposedVersion)
        } else {
            XLog.i(message = "Xposed bridge version: unknown")
        }
        XLog.i(message = "SmsCode version: %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }

    private fun resolveXposedVersion(): Int? {
        return HookEnv.api.getFrameworkVersionCode()?.toInt() ?: HookEnv.api.getApiVersion()
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
        XLog.i(message = "Hooking InboundSmsHandler constructor for android v34+")
        val result = InboundSmsHookInstaller.installConstructorHook(
            classLoader = classLoader,
            className = SMS_HANDLER_CLASS,
        ) { param -> ConstructorHook().afterHookedMethod(param) }
        logInstallResult("constructor", result)
    }

    private fun hookDispatchIntent(classloader: ClassLoader) {
        // minSdkVersion 35: Only hook for Android 10+ / 15+
        hookDispatchIntent29(classloader)
    }

    private fun hookSmsDispatcherChain(classLoader: ClassLoader) {
        // Some ROMs/Android versions may dispatch SMS via alternative paths.
        InboundSmsHookInstaller.installDispatcherChainHooks(classLoader) { className, methodName, param ->
            onDispatcherMethodHooked(className, methodName, param)
        }
    }

    /** Body of a dispatcher-chain hook; the lookup and installation live in core. */
    private fun onDispatcherMethodHooked(
        className: String,
        methodName: String,
        param: MethodHookParam,
    ) {
        XLog.withRoute(LogRoute.SMS_HOOK.id) {
            val intent = VerificationSmsIntentHookSupport.extractOrBuildSmsIntent(
                param.args,
                fallbackAction = Telephony.Sms.Intents.SMS_DELIVER_ACTION,
            )
            val action = intent?.action ?: VerificationSmsIntentHookSupport.extractIntentAction(param.args)
            val pluginContext = runtimeSession.currentOrResolve()?.pluginContext
            if (isVerboseDiagEnabled(pluginContext)) {
                XLog.d(
                    message = "Diag SMS dispatch chain: class=%s owner=%s method=%s action=%s args=%d",
                    className,
                    param.thisObject?.javaClass?.name ?: className,
                    methodName,
                    action ?: "<none>",
                    param.args.size,
                )
            }
            if (className == SMS_HANDLER_CLASS) {
                maybeBlockFromDispatchChain(methodName, param, intent)
            }
        }
    }

    // Android 10+
    private fun hookDispatchIntent29(classLoader: ClassLoader) {
        XLog.d(message = "Hooking dispatchIntent() for Android v29+")
        val result = InboundSmsHookInstaller.installDispatchIntentHook(
            classLoader = classLoader,
            className = SMS_HANDLER_CLASS,
        ) { param, receiverIndex -> DispatchIntentHook(receiverIndex).beforeHookedMethod(param) }
        logInstallResult("dispatchIntent", result)
    }

    /**
     * Minimal install diagnostics shared with the relay host: a target miss means the ROM
     * does not carry the expected class or method, which is the one install failure the
     * installer can see and the hook itself cannot recover from.
     */
    private fun logInstallResult(target: String, result: InboundSmsHookInstaller.Result) {
        if (!result.classFound) {
            XLog.w(message = "SmsHandlerHook install miss: target=%s class not found", target)
            return
        }
        if (result.hookedMethodCount == 0) {
            XLog.w(message = "SmsHandlerHook install miss: target=%s no method hooked", target)
            return
        }
        if (result.hookedMethodCount > 1) {
            XLog.i(
                message = "SmsHandlerHook install overloads: target=%s count=%d",
                target,
                result.hookedMethodCount,
            )
        }
    }

    private inner class ConstructorHook : MethodHook() {
        @Throws(Throwable::class)
        override fun afterHookedMethod(param: MethodHookParam) {
            XLog.withRoute(LogRoute.SMS_HOOK.id) {
                try {
                    afterConstructorHandler(param)
                    runtimeSession.currentOrResolve()?.let { runtime ->
                        replayPendingSms(runtime.pluginContext, runtime.phoneContext)
                    }
                } catch (e: Throwable) {
                    // Never re-throw inside the telephony process: an escaped exception
                    // kills com.android.phone and takes down SMS for the whole device.
                    XLog.e(message = "Error occurred in constructor hook", e)
                }
            }
        }
    }

    private fun afterConstructorHandler(param: MethodHookParam) {
        val context = param.args.getOrNull(1) as? Context ?: return
        constructorInitializer.handle(context)
    }

    private fun initNotificationChannel(runtime: SmsHookRuntimeContext) {
        val channelId = NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION
        val channelName = runtime.pluginContext.getString(R.string.hook_smscode_channel)
        HookRuntimeBridge.notificationAccess.createNotificationChannel(
            runtime.phoneContext,
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH,
        )
        XLog.d(message = "Init notification channel succeed")
    }

    private fun registerCopyCodeReceiver(runtime: SmsHookRuntimeContext) {
        val pluginContext = runtime.pluginContext
        if (!HookRuntimeBridge.prefsAccess.showCodeNotification(pluginContext)) return
        CopyCodeReceiver.registerMe(runtime.phoneContext)
        XLog.d(message = "Register copy code receiver")
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
                message = "SmsInboxObserver shared register skip: key=%s pid=%d ageMs=%d",
                observerKey,
                android.os.Process.myPid(),
                observerAge,
            )
            return
        }
        smsInboxObserver = SmsInboxObserver(pluginContext, phoneContext).also { it.register() }
    }

    private inner class DispatchIntentHook(private val mReceiverIndex: Int) : MethodHook() {
        @Throws(Throwable::class)
        override fun beforeHookedMethod(param: MethodHookParam) {
            XLog.withRoute(LogRoute.SMS_HOOK.id) {
                try {
                    beforeDispatchIntentHandler(param, mReceiverIndex)
                } catch (e: Throwable) {
                    XLog.e(message = "Error occurred in dispatchIntent() hook, ", e)
                }
            }
        }
    }

    private fun beforeDispatchIntentHandler(param: MethodHookParam, receiverIndex: Int) {
        val intent = param.args.getOrNull(0) as? Intent ?: return
        val action = intent.action

        if (BuildConfig.DEBUG) {
            XLog.d(message = "SmsHandlerHook: Received intent action: $action")
            intent.extras?.let { bundle ->
                XLog.d(message = "SmsHandlerHook: Extra keys = %s", bundle.keySet().joinToString(","))
            }
        }

        if (!VerificationSmsIntentHookSupport.isSmsAction(action)) {
            return
        }
        val eventId = VerificationSmsIntentHookSupport.ensureEventId(intent)
        val runtime = ensureRuntimeForDispatch(param, receiverIndex)
        val pluginContext = runtime?.pluginContext
        val phoneContext = runtime?.phoneContext
        if (pluginContext == null || phoneContext == null) {
            // The constructor hook may not have fired yet (or a hot module update left the
            // runtime uninitialised). Park the dispatch briefly instead of dropping the SMS;
            // afterConstructorHandler replays it once the runtime exists.
            val parked = pendingSmsReplay.enqueue(
                intent = intent,
                eventId = eventId,
                source = "dispatch_intent",
                hookArgs = param.args,
                receiver = param.args.getOrNull(receiverIndex),
                inboundSmsHandler = param.thisObject,
            )
            XLog.e(
                message = "Context is null, parked sms for replay. parked=%s pluginContext: %s, phoneContext: %s",
                parked,
                pluginContext,
                phoneContext,
            )
            emitHandler(
                result = if (parked) "skip" else "error",
                reason = if (parked) "context_null_parked" else "context_null_parked_full",
                stage = "sms_handler",
                eventIdPresent = true,
                statusOk = false,
            )
            return
        }
        replayPendingSms(pluginContext, phoneContext)
        if (shouldSkipDispatchBySharedDedup(pluginContext, eventId, action)) {
            emitHandler(
                result = "skip",
                reason = "shared_store_dedupe",
                stage = "dedupe_shared",
                eventIdPresent = true,
            )
            return
        }
        if (VerificationSmsIntentHookSupport.markDispatchHandled(intent, action, DISPATCH_HANDLER_KEY)) {
            XLog.d(
                message = "Diag SMS dispatch duplicate skip: event_id=%s action=%s source=intent_extra",
                eventId,
                action,
            )
            emitHandler(
                result = "skip",
                reason = "intent_extra_dedupe",
                stage = "dedupe_intent",
                eventIdPresent = true,
            )
            return
        }
        val pduCount = VerificationSmsIntentHookSupport.getPduCount(intent) {
            XLog.w(message = "Diag getPduCount failed: %s", it.message ?: "unknown")
        }
        // Extras first; if the sender omitted them, walk the handler and its arguments
        // for the SIM slot. relay already did this for its forward path, so both hosts
        // now resolve routing the same way and the record keeps the slot either way.
        val routing = SmsRoutingIntentExtras.readFrom(intent)
        val resolvedRouting = if (routing.hasValue()) {
            routing
        } else {
            SmsRoutingProbeResolver.ensureSimRoutingExtras(
                intent = intent,
                handler = param.thisObject,
                args = param.args,
            ) ?: routing
        }
        XLog.i(
            message = "Diag SMS intent intercepted: event_id=%s action=%s pduCount=%d extras=%s simSlot=%d subId=%d",
            eventId,
            action,
            pduCount,
            intent.extras != null,
            resolvedRouting.simSlot ?: -1,
            resolvedRouting.subId ?: 0,
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
        emitHandler(
            result = "ok",
            reason = when {
                outcome.inboundBlocked -> "inbound_blocked"
                outcome.shouldStopDispatch -> "stop_dispatch"
                else -> "handled"
            },
            stage = "sms_handler",
            eventIdPresent = true,
        )
        if (outcome.shouldStopDispatch) {
            return
        }
    }

    /**
     * Replays dispatches that arrived before the plugin context existed.
     *
     * A parked dispatch has already been handed to the system by the time we can
     * replay it, so the replay can only redo the side-effect work (parse, blacklist
     * evaluation, forwarding). It cannot retroactively cancel system delivery, which
     * is why inbound-blocked is reported separately instead of being treated as a
     * successful block.
     */
    private fun replayPendingSms(pluginContext: Context, phoneContext: Context) {
        val (ready, expired) = pendingSmsReplay.drainReady()
        if (expired > 0) {
            XLog.w(message = "Pending sms replay expired %d dispatch(es)", expired)
        }
        if (ready.isEmpty()) return
        XLog.i(message = "Replaying %d parked sms dispatch(es)", ready.size)
        ready.forEach { entry ->
            runCatching {
                val outcome = dispatchIntentHandler.handle(
                    intent = entry.intent,
                    eventId = entry.eventId,
                    inboundSmsHandler = entry.inboundSmsHandler,
                    receiver = entry.receiver,
                )
                emitHandler(
                    result = "ok",
                    reason = if (outcome.inboundBlocked) {
                        "replay_side_effects_only_block_too_late"
                    } else {
                        "replay_handled"
                    },
                    stage = "sms_handler_replay",
                    eventIdPresent = true,
                    statusOk = !outcome.inboundBlocked,
                )
            }.onFailure {
                XLog.e(message = "Pending sms replay failed: event_id=%s %s", entry.eventId, it.message ?: "unknown")
            }
        }
    }

    private fun scheduleBlacklistDelete(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) {
        smsOperationExecutor.execute {
            XLog.withRoute(LogRoute.SMS_HOOK.id) {
                runCatching {
                    OperateSmsAction(
                        pluginContext,
                        phoneContext,
                        smsMsg,
                        OperateSmsAction.FORCE_DELETE,
                    ).call()
                }.onFailure {
                    XLog.w(message = "Diag sms blacklist delete task failed: %s", it.message ?: "unknown")
                }
            }
        }
    }

    private fun ensureRuntimeForDispatch(param: MethodHookParam, receiverIndex: Int): SmsHookRuntimeContext? {
        runtimeSession.currentOrResolve()?.let { return it }
        XLog.w(
            message = "SmsHandlerHook dispatch runtime missing, attempt recovery: owner=%s receiverIndex=%d argCount=%d",
            param.thisObject?.javaClass?.name ?: "<none>",
            receiverIndex,
            param.args.size,
        )
        val recovered = resolveDispatchPhoneContext(param, receiverIndex) ?: run {
            XLog.e(
                message = "SmsHandlerHook dispatch runtime recovery skipped: no phone context owner=%s receiverIndex=%d argCount=%d",
                param.thisObject?.javaClass?.name ?: "<none>",
                receiverIndex,
                param.args.size,
            )
            return null
        }
        val recoveredPhoneContext = recovered.context
        XLog.w(
            message = "SmsHandlerHook dispatch runtime recovery context: source=%s package=%s process=%s",
            recovered.source,
            recoveredPhoneContext.packageName,
            recoveredPhoneContext.applicationInfo?.processName ?: recoveredPhoneContext.packageName,
        )
        val outcome = constructorInitializer.handle(recoveredPhoneContext)
        if (!outcome.initialized) {
            XLog.e(
                message = "SmsHandlerHook dispatch runtime recovery failed: source=%s reason=%s package=%s",
                recovered.source,
                outcome.stopReason ?: "unknown",
                recoveredPhoneContext.packageName,
            )
            return null
        }
        return runtimeSession.currentOrResolve().also { runtime ->
            if (runtime != null) {
                XLog.w(
                    message = "SmsHandlerHook dispatch runtime recovered: source=%s package=%s process=%s",
                    recovered.source,
                    runtime.phoneContext.packageName,
                    runtime.phoneContext.applicationInfo?.processName ?: runtime.phoneContext.packageName,
                )
            }
        }
    }

    private fun resolveDispatchPhoneContext(param: MethodHookParam, receiverIndex: Int): SmsHookContextRecovery.RecoveredContext? {
        SmsHookContextRecovery.resolveFirst(
            param.thisObject to "handler",
            param.args.getOrNull(receiverIndex) to "receiver",
        )?.let { return it }
        param.args.forEachIndexed { index, arg ->
            SmsHookContextRecovery.resolve(arg, "arg[$index]")?.let { return it }
        }
        return null
    }

    private fun shouldSkipDispatchBySharedDedup(
        pluginContext: Context,
        eventId: String,
        action: String?,
    ): Boolean {
        return runCatching {
            if (eventId.isBlank() || action.isNullOrBlank()) return false
            val claim = HookRuntimeBridge.contentProviderAccess.claimRuntimeGate(
                context = pluginContext,
                fileName = SmsDispatchIntentDeduplicator.DEFAULT_FILE_NAME,
                keys = listOf("$eventId|$action"),
                windowMs = SmsDispatchIntentDeduplicator.DEFAULT_WINDOW_MS,
                maxEntries = SmsDispatchIntentDeduplicator.DEFAULT_MAX_ENTRIES,
            )
            if (!claim.claimed) {
                XLog.d(
                    message = "Diag SMS dispatch duplicate skip: event_id=%s action=%s source=shared_store ageMs=%d",
                    eventId,
                    action,
                    claim.ageMs ?: 0L,
                )
            }
            !claim.claimed
        }.onFailure {
            XLog.w(
                message = "Diag SMS dispatch shared dedup failed: event_id=%s action=%s err=%s",
                eventId,
                action,
                it.message ?: it.javaClass.simpleName,
            )
        }.getOrDefault(false)
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
        val runtime = ensureRuntimeForDispatch(param, receiverIndex = -1) ?: return
        val pluginContext = runtime.pluginContext
        val phoneContext = runtime.phoneContext
        if (!runCatching { HookRuntimeBridge.prefsAccess.isEnabled(pluginContext) }.getOrDefault(false)) {
            return
        }
        if (!runCatching { HookRuntimeBridge.prefsAccess.mobileAutomationAllowed(pluginContext) }.getOrDefault(false)) {
            XLog.i(message = "Mobile entitlement gate skipped dispatch-chain side effects")
            return
        }
        val eventId = VerificationSmsIntentHookSupport.ensureEventId(intent)
        if (ModuleConflictArbiter.shouldSuppressByRelay(phoneContext, "SmsHandlerHook#$methodName")) {
            logSuppressedOnce("dispatchChain:$methodName")
            SmsCodeConflictNoticeHelper.notifyConflictOnSms(pluginContext, phoneContext, eventId)
            return
        }
        val evaluation = SmsBlockEvaluator.evaluate(pluginContext, intent, eventId, "dispatch_chain") ?: return
        if (evaluation.blacklistDeleteOnly && evaluation.smsMsg != null) {
            scheduleBlacklistDelete(pluginContext, phoneContext, evaluation.smsMsg)
        }
        val reason = evaluation.blockReason ?: return
        if (shouldSkipDispatchChainBlock(evaluation.smsMsg, action, reason)) {
            return
        }
        runtimeSession.recordHeartbeat("sms_handler_dispatch_chain")
        XLog.w(
            message = "Diag SMS dispatch chain block: method=%s reason=%s event_id=%s",
            methodName,
            reason,
            eventId,
        )
        // CodeWorker.parse() is async: only does toast/notification/clipboard side effects.
        // The block decision was already made above. Don't block the dispatch chain.
        smsOperationExecutor.execute {
            CodeWorker(pluginContext, phoneContext, intent, eventId).parse()
        }
        val inbound = param.thisObject ?: return
        val smsReceiver = VerificationSmsIntentHookSupport.findRawTableReceiver(param.args)
        if (smsReceiver != null) {
            val blocked = inboundSmsBlocker.blockInboundSms(
                inboundSmsHandler = inbound,
                smsReceiver = smsReceiver,
                reason = reason,
                eventId = eventId,
            )
            if (!blocked) {
                XLog.w(
                    message = "Diag dispatch chain block aborted: cleanup incomplete method=%s event_id=%s",
                    methodName,
                    eventId,
                )
                return
            }
        } else {
            XLog.w(
                message = "Diag dispatch chain block fallback: receiver unavailable method=%s event_id=%s",
                methodName,
                eventId,
            )
            return
        }
        param.result = VerificationSmsIntentHookSupport.defaultResultForType((param.method as? Method)?.returnType)
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
                message = "Diag dispatch chain block duplicate skip: action=%s reason=%s ageMs=%d",
                action,
                reason,
                result.ageMs ?: 0L,
            )
            return true
        }
        return false
    }


    private fun emitHandler(
        result: String,
        reason: String,
        stage: String,
        eventIdPresent: Boolean = false,
        statusOk: Boolean = true,
        errorClass: String? = null,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "hook",
            "stage" to stage,
            "reason" to reason,
            "source" to "sms_handler",
            "msg_type" to "sms",
        )
        if (eventIdPresent) {
            attrs["event_id_present"] = "true"
        }
        if (!errorClass.isNullOrBlank()) {
            attrs["error_class"] = errorClass
        }
        MagiskOtel.event(name = "sms.process", attributes = attrs, statusOk = statusOk)
    }

    private fun logSuppressedOnce(stage: String) {
        if (suppressionLogged) return
        synchronized(this) {
            if (suppressionLogged) return
            XLog.w(
                message = "SmsHandlerHook suppressed: reason=%s stage=%s package=%s",
                ModuleConflictArbiter.SUPPRESSION_REASON,
                stage,
                ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            )
            suppressionLogged = true
        }
    }

    private fun isVerboseDiagEnabled(context: Context?): Boolean {
        if (context == null) return false
        return runCatching { HookRuntimeBridge.prefsAccess.isVerboseLogMode(context) }.getOrDefault(false)
    }

    companion object {
        const val ANDROID_PHONE_PACKAGE = "com.android.phone"
        const val XIAOMI_PHONE_PACKAGE = "com.xiaomi.phone"
        private const val TELEPHONY_PACKAGE = "com.android.internal.telephony"
        private const val SMS_HANDLER_CLASS = "$TELEPHONY_PACKAGE.InboundSmsHandler"
        private val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private const val DISPATCH_HANDLER_KEY = "sms_handler"
        private val installedHookKeys = Collections.synchronizedSet(mutableSetOf<String>())
        private val dispatchChainBlockDeduplicator = SmsDispatchChainBlockDeduplicator()

        fun isSmsHandlerPackage(packageName: String): Boolean {
            return packageName == ANDROID_PHONE_PACKAGE || packageName == XIAOMI_PHONE_PACKAGE
        }

        private fun buildHookInstallKey(param: LoadParam): String =
            hookClaim.key("hook_install", param.packageName, param.processName)

        private fun buildSharedProcessKey(
            prefix: String,
            packageName: String,
            processName: String,
        ): String = hookClaim.key(prefix, packageName, processName)

        private fun claimProcessPropertyWithinWindow(
            key: String,
            windowMs: Long,
        ): Long? = hookClaim.claim(key)

        private fun markHookInstalled(key: String): Boolean = hookClaim.markInstalled(key)

        private val hookClaim = InboundSmsHookClaim()

        private const val SHARED_HOOK_INIT_WINDOW_MS = 5 * 60 * 1000L
        private const val SHARED_OBSERVER_WINDOW_MS = 5 * 60 * 1000L
    }
}

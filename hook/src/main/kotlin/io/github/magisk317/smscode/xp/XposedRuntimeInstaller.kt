package io.github.magisk317.smscode.xp

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import io.github.magisk317.smscode.common.utils.HookPrefsReader
import io.github.magisk317.smscode.common.utils.ProviderIpcTokenGate
import io.github.magisk317.smscode.common.utils.RuntimeDiagnosticsBridge
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.smscode.data.db.DBProvider
import io.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.SmsDuplicateCheckResult
import io.github.magisk317.smscode.verification.resolveSmsDuplicateCheck
import io.github.magisk317.smscode.data.log.RuntimeLogProvider
import io.github.magisk317.smscode.runtime.BuildConfig as RuntimeBuildConfig
import io.github.magisk317.smscode.runtime.RuntimeCodeRecordRestoreFacade
import io.github.magisk317.smscode.runtime.RuntimeNotificationFacade
import io.github.magisk317.smscode.runtime.RuntimeStorageFacade
import io.github.magisk317.smscode.runtime.bridge.HookContentProviderAccess
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeGateClaimResult
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.AnonymousInstallationId
import io.github.magisk317.xposed.capability.runtime.CoreHookPolicy
import io.github.magisk317.xposed.capability.runtime.CoreHookPolicyHolder
import io.github.magisk317.xposed.capability.runtime.CoreLogSink
import io.github.magisk317.xposed.capability.runtime.CoreLogSinkHolder
import io.github.magisk317.xposed.capability.runtime.CoreRuntime
import io.github.magisk317.xposed.capability.runtime.CoreRuntimeAccess
import io.github.magisk317.xposed.logging.XposedLogClient

@SuppressLint("LogNotTimber", "PrivateApi", "StaticFieldLeak")
object XposedRuntimeInstaller {

    @Volatile
    private var logSinkInstalled = false

    @Volatile
    private var moduleContext: Context? = null

    fun installEntryRuntime() {
        CoreRuntime.install(object : CoreRuntimeAccess {
            override val logTag: String = BuildConfig.LOG_TAG
            override val logLevel: Int = RuntimeBuildConfig.LOG_LEVEL
            override val logToXposed: Boolean = RuntimeBuildConfig.LOG_TO_XPOSED
            override val debug: Boolean = BuildConfig.DEBUG
            override val applicationId: String = BuildConfig.APPLICATION_ID
            override val actionNamespace: String = "io.github.magisk317.smscode"
        })
        CoreHookPolicyHolder.install(object : CoreHookPolicy {
            override fun shouldSuppressSystemHooks(context: Context?, source: String): Boolean {
                return ModuleConflictArbiter.shouldSuppressByRelay(context, source)
            }
        })
        XposedLogClient.configure(
            authority = RuntimeLogProvider.authority(BuildConfig.APPLICATION_ID),
            source = "smscode",
        )
        val hookApp = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? android.content.Context
        }.getOrNull()
        val otelEnabled = if (hookApp != null) {
            BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD || HookPrefsReader.isAnalyticsEnabled(hookApp)
        } else {
            true
        }
        // The hook process has no local preference file of its own. Installing the snapshot here
        // (rather than only on the first SMS) gives every preference read a persistent fallback
        // as soon as one provider hit has been recorded.
        HookPrefsReader.installSnapshot(hookApp)
        MagiskOtel.configureIfAbsent(
            MagiskOtel.Config(
                enabled = otelEnabled,
                serviceName = "xposedsmscode",
                serviceVersion = BuildConfig.VERSION_NAME,
                projectId = "83955172",
                projectName = "XposedSmsCode",
                environment = if (BuildConfig.DEBUG) "debug" else if (BuildConfig.IS_CI_BUILD) "ci" else "release",
                // Captured once here, but the provider below is what actually keeps the value
                // alive: at hook-install time the remote prefs provider is often not bound yet,
                // and freezing "" here used to drop service.instance.id for the whole process
                // (v3.3.10 measured 0% coverage while commit/device attributes stayed at 100%).
                serviceInstanceId = HookPrefsReader.getStringPreference(
                    AnonymousInstallationId.PREFERENCE_KEY,
                    "",
                ),
                serviceInstanceIdProvider = {
                    HookPrefsReader.getStringPreference(AnonymousInstallationId.PREFERENCE_KEY, "")
                },
                serviceCommit = BuildConfig.COMMIT_HASH,
                deviceAttributes =
                    mapOf(
                        "device.manufacturer" to Build.MANUFACTURER,
                        "device.model" to Build.MODEL,
                        "os.name" to "android",
                        "os.version" to Build.VERSION.RELEASE,
                        "os.api_level" to Build.VERSION.SDK_INT.toString(),
                        // Lets the cloud side split play from sideload installs,
                        // which share version and commit.
                        "distribution.channel" to BuildConfig.MOBILE_ENTITLEMENT_CHANNEL,
                    ),
                suppressedResultValues = setOf("skip"),
            ),
        )
        installHookBridge()
        installLogSink()
    }

    /**
     * Wires the extracted :hook module's [HookRuntimeBridge] to the concrete
     * runtime Facades. Must run in every hooked process before any hook fires,
     * otherwise hook code hits `HookRuntimeBridge.*Access` "not installed" errors.
     */
    private fun installHookBridge() {
        HookRuntimeBridge.install(
            prefs = HookPrefsReader,
            notification = RuntimeNotificationFacade,
            storage = RuntimeStorageFacade,
            codeRecord = RuntimeCodeRecordRestoreFacade,
            contentProvider = object : HookContentProviderAccess {
                override fun smsMsgContentUri(context: Context): Uri =
                    DBProvider.smsMsgContentUri(context)

                override fun appInfoContentUri(context: Context): Uri =
                    DBProvider.appInfoContentUri(context)

                override fun autoInputEventContentUri(context: Context): Uri =
                    DBProvider.autoInputEventContentUri(context)

                override fun authority(context: Context): String =
                    DBProvider.authority(context)

                override fun querySmsMsgByFingerprint(
                    context: Context,
                    sender: String?,
                    body: String,
                    date: Long,
                ): SmsDuplicateCheckResult {
                    val token = HookPrefsReader.getIpcToken(context)
                    return resolveSmsDuplicateCheck(token) {
                        val uri = DBProvider.smsMsgContentUri(context)
                            .buildUpon()
                            .appendQueryParameter(ProviderIpcTokenGate.PARAM_IPC_TOKEN, token)
                            .build()
                        context.contentResolver.query(
                            uri,
                            arrayOf("_id"),
                            "msg_type = ? AND sender = ? AND body = ? AND date = ?",
                            arrayOf(SmsMsg.MSG_TYPE_SMS.toString(), sender.orEmpty(), body, date.toString()),
                            null,
                        )?.use { it.moveToFirst() }
                    }
                }

                override fun claimRuntimeGate(
                    context: Context,
                    fileName: String,
                    keys: Collection<String>,
                    windowMs: Long,
                    maxEntries: Int,
                ): HookRuntimeGateClaimResult = DBProvider.claimRuntimeGate(
                    context = context,
                    fileName = fileName,
                    keys = keys,
                    windowMs = windowMs,
                    maxEntries = maxEntries,
                    token = HookPrefsReader.getIpcToken(context),
                )

                override fun recordHookHeartbeat(
                    context: Context,
                    packageName: String,
                    processName: String,
                    source: String,
                    verboseLogging: Boolean,
                    route: String,
                ): Boolean = DBProvider.recordHookHeartbeat(
                    context = context,
                    packageName = packageName,
                    processName = processName,
                    source = source,
                    verboseLogging = verboseLogging,
                    route = route,
                    token = HookPrefsReader.getIpcToken(context),
                )
            },
        )
        HookRuntimeBridge.hookProcessInit = { context -> ensureHookProcessLogging(context) }
    }

    fun ensureHookProcessLogging(moduleContext: Context) {
        this.moduleContext = moduleContext.applicationContext ?: moduleContext
        // The hook process is its own JVM: it has to install the same build-derived policy the
        // app installed, because nothing carries it across the process boundary.
        val policy = MagiskLogPolicy.install(
            isDebugBuild = BuildConfig.BUILD_TYPE == "debug",
            allowPlaintextLogs = BuildConfig.ALLOW_PLAINTEXT_LOGS,
        )
        RuntimeDiagnosticsBridge.ensureInstalled()
        RuntimeLogStore.initialize(this.moduleContext ?: moduleContext, enableDetailedLogs = policy.verboseLogging)
        // Cross-process cache invalidation: listen for module-app notifyChange signals.
        io.github.magisk317.smscode.common.utils.HookCacheInvalidator.register(this.moduleContext!!)
        installLogSink()
    }

    private fun installLogSink() {
        if (logSinkInstalled) return
        synchronized(this) {
            if (logSinkInstalled) return
            XposedLogClient.attachContext(moduleContext ?: return@synchronized)
            android.util.Log.w("smscode", "installLogSink: installing CoreLogSink, moduleContext=${moduleContext != null}")
            CoreLogSinkHolder.install(object : CoreLogSink {
                override fun append(
                    priority: Int,
                    tag: String,
                    message: String,
                    force: Boolean,
                    route: String?,
                    sensitive: Boolean,
                    throwableText: String?,
                ) {
                    val safeMessage = if (sensitive) {
                        DefaultLogSanitizer.sanitizeIfEnabled(message)
                    } else {
                        message
                    }
                    XposedLogClient.append(priority, tag, safeMessage, force, route, sensitive, throwableText)
                }
            })
            logSinkInstalled = true
        }
    }
}

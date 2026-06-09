package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.github.magisk317.smscode.receiver.KillSelfControlReceiver
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputResultBroadcastContract
import io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook
import io.github.magisk317.smscode.xposed.utils.XLog
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction

class KillMeAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val attemptId: Long? = null,
    private val notificationAutoCancelDelayMs: Long? = null,
) : CallableAction(pluginContext, phoneContext, smsMsg) {

    private val mHandler = Handler(Looper.getMainLooper())
    private var mResultReceiver: BroadcastReceiver? = null
    private var mTimeoutRunnable: Runnable? = null
    private val mKillLock = Any()
    private var mActionStarted = false
    private var mWaitResolved = false
    private var mWaitResolveSuccess = false
    private var mWaitResolveReason = "pending"
    private var mKillChainStarted = false

    fun armAutoInputResultListener() {
        if (attemptId == null) return
        ensureAutoInputResultListenerArmed()
    }

    override fun action(): Bundle? {
        if (attemptId == null) {
            killMe()
            return null
        }

        ensureAutoInputResultListenerArmed()
        val resolved = synchronized(mKillLock) {
            mActionStarted = true
            if (!mWaitResolved) {
                armWaitTimeoutLocked()
            }
            mWaitResolved
        }
        if (resolved) {
            startKillChainIfNeeded()
        } else {
            XLog.w(
                "KillMeAction: waiting for auto-input result: attemptId=%d timeoutMs=%d",
                attemptId,
                KILL_RESULT_TIMEOUT_MS,
            )
        }
        return null
    }

    private fun ensureAutoInputResultListenerArmed() {
        val resolvedAttemptId = attemptId ?: return
        var receiverToRegister: BroadcastReceiver? = null
        synchronized(mKillLock) {
            if (mWaitResolved || mResultReceiver != null) {
                return
            }
            mResultReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val result = when (
                        val receiverResult = AutoInputResultBroadcastContract.readResult(
                            intent = intent,
                            expectedAction = SystemInputInjectorHook.resolveActionAutoInputResult(),
                        )
                    ) {
                        AutoInputResultBroadcastContract.ReceiverResult.Ignored -> return
                        AutoInputResultBroadcastContract.ReceiverResult.MissingAttemptId -> return
                        is AutoInputResultBroadcastContract.ReceiverResult.Accepted -> receiverResult.result
                    }
                    if (result.attemptId != resolvedAttemptId) return

                    val success = result.success
                    val reason = result.reason.orEmpty().ifBlank { "n/a" }
                    XLog.w(
                        "KillMeAction: auto-input result received: attemptId=%d success=%s reason=%s",
                        resolvedAttemptId,
                        success,
                        reason,
                    )
                    resolveAutoInputWait(success, reason)
                }
            }
            receiverToRegister = mResultReceiver
        }

        val receiver = receiverToRegister ?: return
        runCatching {
            val filter = IntentFilter(SystemInputInjectorHook.resolveActionAutoInputResult())
            mPhoneContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        }.onSuccess {
            XLog.w(
                "KillMeAction: auto-input result receiver armed: attemptId=%d",
                resolvedAttemptId,
            )
        }.onFailure { error ->
            XLog.w(
                "KillMeAction: result receiver registration failed: %s",
                error.message ?: error.javaClass.simpleName,
            )
            resolveAutoInputWait(success = false, reason = "receiver_register_failed")
        }
    }

    private fun armWaitTimeoutLocked() {
        if (mTimeoutRunnable != null || mWaitResolved) return
        val timeout = Runnable {
            XLog.w(
                "KillMeAction: auto-input result timeout (%d ms), proceeding with kill",
                KILL_RESULT_TIMEOUT_MS,
            )
            resolveAutoInputWait(success = false, reason = "timeout")
        }
        mTimeoutRunnable = timeout
        mHandler.postDelayed(timeout, KILL_RESULT_TIMEOUT_MS)
    }

    private fun resolveAutoInputWait(success: Boolean, reason: String) {
        var receiverToUnregister: BroadcastReceiver? = null
        var shouldStartKillChain = false
        val normalizedReason = reason.ifBlank { "n/a" }
        synchronized(mKillLock) {
            if (mWaitResolved) return
            mWaitResolved = true
            mWaitResolveSuccess = success
            mWaitResolveReason = normalizedReason
            receiverToUnregister = mResultReceiver.also { mResultReceiver = null }
            mTimeoutRunnable?.let { mHandler.removeCallbacks(it) }
            mTimeoutRunnable = null
            shouldStartKillChain = mActionStarted
        }

        receiverToUnregister?.let { receiver ->
            runCatching { mPhoneContext.unregisterReceiver(receiver) }
                .onFailure { XLog.w("KillMeAction: unregister result receiver failed") }
        }

        if (shouldStartKillChain) {
            XLog.w(
                "KillMeAction: auto-input wait resolved: attemptId=%d success=%s reason=%s",
                attemptId ?: -1L,
                success,
                normalizedReason,
            )
            startKillChainIfNeeded()
        }
    }

    private fun startKillChainIfNeeded() {
        val shouldStart = synchronized(mKillLock) {
            if (mKillChainStarted) {
                false
            } else {
                mKillChainStarted = true
                true
            }
        }
        if (!shouldStart) return
        XLog.w(
            "KillMeAction: start kill chain: attemptId=%d success=%s reason=%s",
            attemptId ?: -1L,
            mWaitResolveSuccess,
            mWaitResolveReason,
        )
        proceedWithKillChain()
    }

    private fun proceedWithKillChain() {
        val cancelDelay = notificationAutoCancelDelayMs?.takeIf { it > 0L }
        if (cancelDelay != null) {
            val extraBuffer = NOTIFICATION_CANCEL_BUFFER_MS
            val totalDelay = cancelDelay + extraBuffer
            mHandler.postDelayed({ killMe() }, totalDelay)
            XLog.w("KillMeAction: waiting for notification auto-cancel (%d ms + %d ms buffer) before kill", cancelDelay, extraBuffer)
        } else {
            killMe()
        }
    }

    private fun killMe() {
        if (!PrefsReader.killMeEnabled(mPluginContext)) return
        if (requestSelfKillPrimary()) {
            return
        }
        XLog.w("KillMeAction: provider self-kill failed for process %s", mPhoneContext.packageName)
    }

    private fun requestSelfKillPrimary(): Boolean {
        return try {
            val token = PrefsReader.getIpcToken(mPluginContext)
            if (token.isBlank()) return false
            val intent = Intent(KillSelfControlReceiver.ACTION_KILL_SELF).apply {
                setClassName(mPluginContext.packageName, KillSelfControlReceiver::class.java.name)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(KillSelfControlReceiver.EXTRA_DELAY_MS, 80L)
                putExtra(KillSelfControlReceiver.EXTRA_IPC_TOKEN, token)
            }
            mPluginContext.sendBroadcast(intent)
            XLog.w("KillMeAction primary requested via KillSelfControlReceiver")
            true
        } catch (e: Throwable) {
            XLog.w("KillMeAction primary failed: %s", e.message ?: e.javaClass.simpleName)
            false
        }
    }

    private companion object {
        private const val KILL_RESULT_TIMEOUT_MS = 8_000L
        private const val NOTIFICATION_CANCEL_BUFFER_MS = 1_500L
    }
}

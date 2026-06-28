package com.github.magisk317.smscode.receiver

import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.runtime.RuntimeStorageFacade
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputBroadcastContract
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputResultBroadcastContract
import io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object AutoInputResultHandler {
    val action: String
        get() = SystemInputInjectorHook.resolveActionAutoInputResult()

    fun handle(context: Context, intent: Intent, onComplete: () -> Unit = {}) {
        runCatching {
            AUTO_INPUT_RESULT_EXECUTOR.execute {
                try {
                    handleOnWorker(context, intent)
                } finally {
                    onComplete()
                }
            }
        }.onFailure { error ->
            XLog.w(
                "AutoInput result worker rejected: %s",
                error.message ?: error.javaClass.simpleName,
            )
            onComplete()
        }
    }

    private fun handleOnWorker(context: Context, intent: Intent) {
        val result = when (
            val receiverResult = AutoInputResultBroadcastContract.readResult(
                intent = intent,
                expectedAction = action,
            )
        ) {
            AutoInputResultBroadcastContract.ReceiverResult.Ignored -> return
            AutoInputResultBroadcastContract.ReceiverResult.MissingAttemptId -> return
            is AutoInputResultBroadcastContract.ReceiverResult.Accepted -> receiverResult.result
        }
        val expectedToken = runBlocking {
            AppPreferencesDataStore.getString(context, PrefConst.KEY_IPC_TOKEN, "")
        }
        val receivedToken = intent.getStringExtra(AutoInputBroadcastContract.EXTRA_IPC_TOKEN).orEmpty()
        if (expectedToken.isBlank() || receivedToken.isBlank() || receivedToken != expectedToken) {
            XLog.w(
                "Diag AutoInputResultReceiver rejected token: attemptId=%d expectedEmpty=%s receivedEmpty=%s",
                result.attemptId,
                expectedToken.isBlank(),
                receivedToken.isBlank(),
            )
            return
        }

        val updatedRows = runCatching {
            RuntimeStorageFacade.dbManager(context).updateAutoInputResult(
                attemptId = result.attemptId,
                success = result.success,
                reason = result.reason,
            )
        }.onFailure { error ->
            XLog.w(
                "AutoInput result persist failed: %s",
                error.message ?: error.javaClass.simpleName,
            )
        }.getOrDefault(0)

        if (updatedRows <= 0) {
            // UPDATE found no row — the INSERT in the hook process may have failed
            // or the WAL hasn't propagated yet. Upsert to ensure the result is persisted.
            val upserted = runCatching {
                RuntimeStorageFacade.dbManager(context).upsertAutoInputResult(
                    attemptId = result.attemptId,
                    success = result.success,
                    reason = result.reason,
                )
            }.onFailure { error ->
                XLog.w(
                    "AutoInput result upsert failed: %s",
                    error.message ?: error.javaClass.simpleName,
                )
            }.getOrDefault(0)
            if (upserted <= 0) {
                XLog.w(
                    "Diag AutoInputResultReceiver skipped stale result: attemptId=%d success=%s reason=%s",
                    result.attemptId,
                    result.success,
                    result.reason ?: "<none>",
                )
            } else {
                XLog.i(
                    "Diag AutoInputResultReceiver recovered stale result via upsert: attemptId=%d success=%s",
                    result.attemptId,
                    result.success,
                )
            }
        }
    }

    private val workerIndex = AtomicInteger(1)
    private val AUTO_INPUT_RESULT_EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoInputResult-${workerIndex.getAndIncrement()}")
    }
}

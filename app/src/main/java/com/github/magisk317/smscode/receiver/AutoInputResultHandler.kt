package com.github.magisk317.smscode.receiver

import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.runtime.RuntimeStorageFacade
import io.github.magisk317.smscode.runtime.common.autoinput.AutoInputResultProcessor
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

    private fun handleOnWorker(context: Context, intent: Intent) = runBlocking {
        val result = when (
            val validation = AutoInputResultProcessor.validate(
                intent = intent,
                expectedAction = action,
                expectedTokenProvider = {
                    AppPreferencesDataStore.getString(context, PrefConst.KEY_IPC_TOKEN, "")
                },
            )
        ) {
            AutoInputResultProcessor.ValidationResult.Ignored -> return@runBlocking
            AutoInputResultProcessor.ValidationResult.MissingAttemptId -> return@runBlocking
            is AutoInputResultProcessor.ValidationResult.RejectedToken -> {
                XLog.w(
                    "Diag AutoInputResultReceiver rejected token: attemptId=%d expectedEmpty=%s receivedEmpty=%s",
                    validation.result.attemptId,
                    validation.expectedTokenEmpty,
                    validation.receivedTokenEmpty,
                )
                return@runBlocking
            }
            is AutoInputResultProcessor.ValidationResult.Accepted -> validation.result
        }

        val outcome = runCatching {
            AutoInputResultProcessor.persist(
                result = result,
                update = {
                    RuntimeStorageFacade.dbManager(context).updateAutoInputResult(
                        attemptId = it.attemptId,
                        success = it.success,
                        reason = it.reason,
                    ).toLong()
                },
                upsert = {
                    runCatching {
                        RuntimeStorageFacade.dbManager(context).upsertAutoInputResult(
                            attemptId = it.attemptId,
                            success = it.success,
                            reason = it.reason,
                        )
                    }.onFailure { error ->
                        XLog.w(
                            "AutoInput result upsert failed: %s",
                            error.message ?: error.javaClass.simpleName,
                        )
                    }.getOrDefault(0L)
                },
            )
        }.onFailure { error ->
            XLog.w(
                "AutoInput result persist failed: %s",
                error.message ?: error.javaClass.simpleName,
            )
        }.getOrNull() ?: return@runBlocking

        when (outcome) {
            AutoInputResultProcessor.PersistenceOutcome.UPDATED -> Unit
            AutoInputResultProcessor.PersistenceOutcome.STALE -> {
                XLog.w(
                    "Diag AutoInputResultReceiver skipped stale result: attemptId=%d success=%s reason=%s",
                    result.attemptId,
                    result.success,
                    result.reason ?: "<none>",
                )
            }
            AutoInputResultProcessor.PersistenceOutcome.UPSERTED -> {
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

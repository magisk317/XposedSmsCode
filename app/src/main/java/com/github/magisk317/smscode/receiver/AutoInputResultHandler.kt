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
import io.github.magisk317.xposed.logging.MagiskOtel

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
            MagiskOtel.event(
                name = "auto.input",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "result_handler",
                    "reason" to "worker_rejected",
                    "error_class" to error.javaClass.simpleName,
                ),
                statusOk = false,
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
            AutoInputResultProcessor.ValidationResult.Ignored -> {
                MagiskOtel.event(
                    name = "auto.input",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "result_handler",
                        "reason" to "ignored",
                    ),
                    statusOk = true,
                )
                return@runBlocking
            }
            AutoInputResultProcessor.ValidationResult.MissingAttemptId -> {
                MagiskOtel.event(
                    name = "auto.input",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "result_handler",
                        "reason" to "missing_attempt_id",
                    ),
                    statusOk = true,
                )
                return@runBlocking
            }
            is AutoInputResultProcessor.ValidationResult.RejectedToken -> {
                XLog.w(
                    "Diag AutoInputResultReceiver rejected token: attemptId=%d expectedEmpty=%s receivedEmpty=%s",
                    validation.result.attemptId,
                    validation.expectedTokenEmpty,
                    validation.receivedTokenEmpty,
                )
                MagiskOtel.event(
                    name = "auto.input",
                    attributes = mapOf(
                        "result" to "error",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "result_handler",
                        "reason" to "token_rejected",
                    ),
                    statusOk = false,
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
            MagiskOtel.event(
                name = "auto.input",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "result_handler",
                    "reason" to "persist_failed",
                    "error_class" to error.javaClass.simpleName,
                ),
                statusOk = false,
            )
        }.getOrNull() ?: return@runBlocking

        when (outcome) {
            AutoInputResultProcessor.PersistenceOutcome.UPDATED -> {
                MagiskOtel.event(
                    name = "auto.input",
                    attributes = mapOf(
                        "result" to if (result.success) "ok" else "error",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "result_handler",
                        "reason" to "updated",
                    ),
                    statusOk = result.success,
                )
            }
            AutoInputResultProcessor.PersistenceOutcome.STALE -> {
                XLog.w(
                    "Diag AutoInputResultReceiver skipped stale result: attemptId=%d success=%s reason=%s",
                    result.attemptId,
                    result.success,
                    result.reason ?: "<none>",
                )
                MagiskOtel.event(
                    name = "auto.input",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "result_handler",
                        "reason" to "stale",
                    ),
                    statusOk = true,
                )
            }
            AutoInputResultProcessor.PersistenceOutcome.UPSERTED -> {
                XLog.i(
                    "Diag AutoInputResultReceiver recovered stale result via upsert: attemptId=%d success=%s",
                    result.attemptId,
                    result.success,
                )
                MagiskOtel.event(
                    name = "auto.input",
                    attributes = mapOf(
                        "result" to if (result.success) "ok" else "error",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "result_handler",
                        "reason" to "upserted",
                    ),
                    statusOk = result.success,
                )
            }
        }
    }

    private val workerIndex = AtomicInteger(1)
    private val AUTO_INPUT_RESULT_EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoInputResult-${workerIndex.getAndIncrement()}")
    }
}

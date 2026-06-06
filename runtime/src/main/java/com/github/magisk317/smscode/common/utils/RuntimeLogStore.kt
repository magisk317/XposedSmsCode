package com.github.magisk317.smscode.common.utils

import android.content.Context
import java.io.File
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogEntry as SharedRuntimeLogEntry
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogFileContent as SharedRuntimeLogFileContent
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogFileInfo as SharedRuntimeLogFileInfo
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogFileSummary as SharedRuntimeLogFileSummary
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore as SharedRuntimeLogStore

typealias RuntimeLogEntry = SharedRuntimeLogEntry
typealias RuntimeLogFileContent = SharedRuntimeLogFileContent
typealias RuntimeLogFileInfo = SharedRuntimeLogFileInfo
typealias RuntimeLogFileSummary = SharedRuntimeLogFileSummary

object RuntimeLogStore {
    const val ROUTE_SMS_HOOK = SharedRuntimeLogStore.ROUTE_SMS_HOOK
    const val ROUTE_NMS_HOOK = SharedRuntimeLogStore.ROUTE_NMS_HOOK
    const val ROUTE_SYSTEM_INPUT = SharedRuntimeLogStore.ROUTE_SYSTEM_INPUT
    const val ROUTE_PERMISSION_HOOK = SharedRuntimeLogStore.ROUTE_PERMISSION_HOOK
    const val ROUTE_FORWARD = SharedRuntimeLogStore.ROUTE_FORWARD
    const val ROUTE_SENDER = SharedRuntimeLogStore.ROUTE_SENDER
    const val ROUTE_ROOT_DB = SharedRuntimeLogStore.ROUTE_ROOT_DB
    const val ROUTE_APP = SharedRuntimeLogStore.ROUTE_APP

    fun initialize(context: Context, enableDetailedLogs: Boolean) {
        RuntimeDiagnosticsBridge.ensureInstalled()
        SharedRuntimeLogStore.initialize(context, enableDetailedLogs)
    }

    fun setEnabled(on: Boolean) {
        RuntimeDiagnosticsBridge.ensureInstalled()
        SharedRuntimeLogStore.setEnabled(on)
    }

    fun isEnabled(): Boolean {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedRuntimeLogStore.isEnabled()
    }

    fun setRetentionDays(days: Int) {
        RuntimeDiagnosticsBridge.ensureInstalled()
        SharedRuntimeLogStore.setRetentionDays(days)
    }

    @Deprecated("Use setRetentionDays; runtime logs now rotate by day.")
    fun setMaxFileSizeMb(sizeMb: Int) {
        setRetentionDays(sizeMb)
    }

    fun append(
        priority: Int,
        tag: String,
        message: String,
        force: Boolean = false,
        route: String? = ROUTE_APP,
    ) {
        RuntimeDiagnosticsBridge.ensureInstalled()
        SharedRuntimeLogStore.append(priority, tag, message, force, route ?: ROUTE_APP)
    }

    fun clear() {
        RuntimeDiagnosticsBridge.ensureInstalled()
        SharedRuntimeLogStore.clear()
    }

    fun query(minutes: Int?, keyword: String?, limit: Int = 600): List<RuntimeLogEntry> {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedRuntimeLogStore.query(minutes, keyword, limit)
    }

    fun exportText(minutes: Int?, keyword: String?, limit: Int = 600): String {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedRuntimeLogStore.exportText(minutes, keyword, limit)
    }

    fun exportToFile(context: Context, minutes: Int?, keyword: String?, limit: Int = 1200): File? {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedRuntimeLogStore.exportToFile(context, minutes, keyword, limit)
    }

    fun summarizeFiles(): RuntimeLogFileSummary {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedRuntimeLogStore.summarizeFiles()
    }

    fun readLogFile(name: String, maxLines: Int = 2000): RuntimeLogFileContent? {
        RuntimeDiagnosticsBridge.ensureInstalled()
        return SharedRuntimeLogStore.readLogFile(name, maxLines)
    }
}

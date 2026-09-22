package com.github.magisk317.smscode.data.db

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import androidx.core.net.toUri
import kotlinx.coroutines.runBlocking
import com.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.ProviderCallerGuard
import com.github.magisk317.smscode.data.db.entity.AppInfo
import com.github.magisk317.smscode.data.db.entity.SmsCodeRule
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeGateClaimResult
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.smscode.runtime.contract.ipc.RuntimeStateProviderContract
import io.github.magisk317.smscode.runtime.common.utils.SharedRuntimeGate
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.smscode.runtime.contract.record.SmsMsgCursorContract
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.Locale

class DBProvider : ContentProvider() {
    private var mDbManager: DBManager? = null
    private lateinit var uriMatcher: UriMatcher
    private lateinit var authority: String

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        mDbManager = DBManager.get(ctx)
        StorageUtils.repairExternalAppDataPermissions(ctx)
        authority = "${ctx.packageName}.db.provider"
        uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, PATH_SMS_MSG, SMS_MSG_DIR)
            addURI(authority, "$PATH_SMS_MSG/#", SMS_MSG_ID)
            addURI(authority, PATH_SMS_CODE_RULE, SMS_CODE_RULE_DIR)
            addURI(authority, "$PATH_SMS_CODE_RULE/#", SMS_CODE_RULE_ID)
            addURI(authority, PATH_APP_INFO, APP_INFO_DIR)
            addURI(authority, "$PATH_APP_INFO/*", APP_INFO_ITEM)
            addURI(authority, PATH_AUTO_INPUT_EVENT, AUTO_INPUT_EVENT_DIR)
        }
        return true
    }

    override fun getType(uri: Uri): String? = null

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!isCallerAllowed()) {
            return Bundle().apply { putBoolean(RuntimeStateProviderContract.RESULT_OK, false) }
        }
        val ctx = context ?: return Bundle().apply {
            putBoolean(RuntimeStateProviderContract.RESULT_OK, false)
        }
        return when (method) {
            RuntimeStateProviderContract.METHOD_CLAIM_RUNTIME_GATE -> {
                val fileName = arg.orEmpty()
                val keys = extras?.getStringArrayList(RuntimeStateProviderContract.EXTRA_KEYS).orEmpty()
                val windowMs = extras?.getLong(RuntimeStateProviderContract.EXTRA_WINDOW_MS, 0L) ?: 0L
                val maxEntries = extras?.getInt(
                    RuntimeStateProviderContract.EXTRA_MAX_ENTRIES,
                    RuntimeStateProviderContract.DEFAULT_MAX_ENTRIES,
                ) ?: RuntimeStateProviderContract.DEFAULT_MAX_ENTRIES
                val result = runCatching {
                    require(windowMs > 0L) { "Invalid runtime gate window" }
                    SharedRuntimeGate.claimAllWithinWindow(
                        file = SharedRuntimeGate.internalGateFile(ctx, fileName),
                        keys = keys,
                        windowMs = windowMs,
                        maxEntries = maxEntries.coerceIn(1, MAX_RUNTIME_GATE_ENTRIES),
                    )
                }.getOrNull()
                Bundle().apply {
                    putBoolean(RuntimeStateProviderContract.RESULT_OK, result != null)
                    // Preserve the existing fail-open behavior if app storage is unavailable.
                    putBoolean(RuntimeStateProviderContract.RESULT_CLAIMED, result?.claimed ?: true)
                    putLong(
                        RuntimeStateProviderContract.RESULT_AGE_MS,
                        result?.ageMs ?: RuntimeStateProviderContract.NO_AGE_MS,
                    )
                    result?.key?.let {
                        putString(RuntimeStateProviderContract.RESULT_BLOCKED_KEY, it)
                    }
                }
            }

            RuntimeStateProviderContract.METHOD_RECORD_HOOK_HEARTBEAT -> {
                val ok = runCatching {
                    ActivationDiagnosticsStore.recordHookHeartbeat(
                        context = ctx,
                        packageName = extras?.getString(RuntimeStateProviderContract.EXTRA_PACKAGE_NAME).orEmpty(),
                        processName = extras?.getString(RuntimeStateProviderContract.EXTRA_PROCESS_NAME).orEmpty(),
                        source = extras?.getString(RuntimeStateProviderContract.EXTRA_SOURCE).orEmpty(),
                        verboseLogging = extras?.getBoolean(
                            RuntimeStateProviderContract.EXTRA_VERBOSE_LOGGING,
                            false,
                        ) ?: false,
                        route = extras?.getString(RuntimeStateProviderContract.EXTRA_ROUTE).orEmpty(),
                    )
                }.isSuccess
                Bundle().apply { putBoolean(RuntimeStateProviderContract.RESULT_OK, ok) }
            }

            RuntimeStateProviderContract.METHOD_GET_RETENTION_DAYS -> getRetentionDays(ctx)
            else -> super.call(method, arg, extras)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (!isCallerAllowed()) return null
        val uriType = uriMatcher.match(uri)
        val outcome = when (uriType) {
            SMS_MSG_DIR -> {
                val smsMsg = values.toSmsMsg()
                val result = db.insertSmsMsgOrGetExisting(
                    smsMsg = smsMsg,
                    deduplicate = parseBooleanValue(values, KEY_DEDUPLICATE, true),
                )
                trimOldRecordsIfNeeded(context!!, smsMsg)
                InsertOutcome(
                    id = result.id,
                    inserted = !result.duplicate,
                    duplicate = result.duplicate,
                )
            }

            AUTO_INPUT_EVENT_DIR -> {
                InsertOutcome(id = addAutoInputEvent(values), inserted = true)
            }

            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
        if (outcome.inserted) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        return Uri.withAppendedPath(uri, outcome.id.toString())
            .buildUpon()
            .apply {
                if (outcome.duplicate) {
                    appendQueryParameter(QUERY_DUPLICATE, "true")
                }
            }
            .build()
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        if (!isCallerAllowed()) return null
        val uriType = uriMatcher.match(uri)
        return when (uriType) {
            SMS_CODE_RULE_DIR -> querySmsCodeRules(projection)
            SMS_CODE_RULE_ID -> querySmsCodeRuleById(projection, uri)
            SMS_MSG_DIR -> querySmsMsgs(projection, selection, selectionArgs, sortOrder)
            SMS_MSG_ID -> querySmsMsgById(projection, uri)
            APP_INFO_DIR -> queryAppInfo(projection, selection, selectionArgs)
            APP_INFO_ITEM -> queryAppInfoByPackageName(projection, uri)
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        if (!isCallerAllowed()) return 0
        val uriType = uriMatcher.match(uri)
        val rowsDeleted: Int = when (uriType) {
            SMS_MSG_DIR -> deleteSmsMsg(selection, selectionArgs)
            SMS_MSG_ID -> uri.lastPathSegment?.toLongOrNull()?.let { db.removeSmsMsgById(it) } ?: 0
            APP_INFO_ITEM -> deleteAppInfoByPackageName(uri)
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
        if (rowsDeleted > 0) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        return rowsDeleted
    }

    private fun deleteSmsMsg(selection: String?, selectionArgs: Array<String>?): Int {
        if (selection == null || selectionArgs.isNullOrEmpty()) {
            return 0
        }
        val normalized = selection.replace("`", "").trim().lowercase()
        if (normalized == "_id = ?" || normalized == "id = ?") {
            val id = selectionArgs.firstOrNull()?.toLongOrNull() ?: return 0
            return db.removeSmsMsgById(id)
        }
        throw IllegalArgumentException("Unsupported delete selection: $selection")
    }

    private fun querySmsCodeRules(projection: Array<String>?): Cursor {
        val rules = db.queryAllSmsCodeRules()
        val columns = columnsOrDefault(projection, SMS_CODE_RULE_DEFAULT_COLUMNS, SMS_CODE_RULE_COLUMNS)
        val cursor = MatrixCursor(columns)
        rules.forEach { rule ->
            cursor.addRow(buildRow(columns) { column -> valueFromSmsCodeRule(rule, column) })
        }
        return cursor
    }

    private fun querySmsMsgs(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        val filteredRows = db
            .queryAllSmsMsg()
            .asSequence()
            .filterSmsMsgs(selection, selectionArgs)
            .toList()
        val sortSpec = parseSortOrder(sortOrder)
        val sortedRows = if (sortSpec.descending) {
            filteredRows.sortedByDescending { it.date }
        } else {
            filteredRows.sortedBy { it.date }
        }
        val rows = sortSpec.limit?.let { sortedRows.take(it) } ?: sortedRows
        val columns = columnsOrDefault(projection, SmsMsgCursorContract.defaultColumns, SMS_MSG_COLUMNS)
        val cursor = MatrixCursor(columns)
        rows.forEach { msg ->
            cursor.addRow(buildRow(columns) { column -> valueFromSmsMsg(msg, column) })
        }
        return cursor
    }

    private fun Sequence<SmsMsg>.filterSmsMsgs(
        selection: String?,
        selectionArgs: Array<String>?,
    ): Sequence<SmsMsg> {
        if (selection.isNullOrBlank()) {
            return this
        }
        val normalizedClauses = selection
            .replace("`", "")
            .split(Regex("(?i)\\s+and\\s+"))
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        var argIndex = 0
        var rows = this
        normalizedClauses.forEach { clause ->
            when (clause) {
                "msg_type = ?" -> {
                    val expected = selectionArgs?.getOrNull(argIndex)?.toIntOrNull()
                    argIndex += 1
                    require(expected != null) { "Missing or invalid msg_type selection arg" }
                    rows = rows.filter { it.msgType == expected }
                }

                "sms_code is not null" -> {
                    rows = rows.filter { !it.smsCode.isNullOrEmpty() }
                }

                "sms_code != ''", "sms_code <> ''" -> {
                    rows = rows.filter { !it.smsCode.isNullOrBlank() }
                }

                "package_name = ?" -> {
                    val expected = selectionArgs?.getOrNull(argIndex)
                    argIndex += 1
                    require(expected != null) { "Missing package_name selection arg" }
                    rows = rows.filter { it.packageName == expected }
                }

                "notify_channel_id != ''", "notify_channel_id <> ''" -> {
                    rows = rows.filter { it.notifyChannelId.isNotBlank() }
                }

                else -> throw IllegalArgumentException("Unsupported sms_msg selection clause: $clause")
            }
        }
        require(argIndex <= selectionArgs.orEmpty().size) { "Unused selection args are not supported" }
        return rows
    }

    private fun querySmsMsgById(projection: Array<String>?, uri: Uri): Cursor {
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw IllegalArgumentException("Invalid URI: $uri")
        val columns = columnsOrDefault(projection, SmsMsgCursorContract.defaultColumns, SMS_MSG_COLUMNS)
        val cursor = MatrixCursor(columns)
        val msg = db.querySmsMsgById(id)
        if (msg != null) {
            cursor.addRow(buildRow(columns) { column -> valueFromSmsMsg(msg, column) })
        }
        return cursor
    }

    private fun querySmsCodeRuleById(projection: Array<String>?, uri: Uri): Cursor {
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw IllegalArgumentException("Invalid URI: $uri")
        val columns = columnsOrDefault(projection, SMS_CODE_RULE_DEFAULT_COLUMNS, SMS_CODE_RULE_COLUMNS)
        val cursor = MatrixCursor(columns)
        val rule = db.querySmsCodeRuleById(id)
        if (rule != null) {
            cursor.addRow(buildRow(columns) { column -> valueFromSmsCodeRule(rule, column) })
        }
        return cursor
    }

    private fun queryAppInfo(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Cursor {
        var rows = db.queryAllAppInfos()
        if (!selection.isNullOrBlank()) {
            val normalized = selection.replace("`", "").trim().lowercase()
            if (normalized == "blocked = ?" && !selectionArgs.isNullOrEmpty()) {
                val blocked = selectionArgs[0] == "1" || selectionArgs[0].equals("true", ignoreCase = true)
                rows = rows.filter { it.blocked == blocked }
            } else if (normalized == "forwarding = ?" && !selectionArgs.isNullOrEmpty()) {
                val forwarding = selectionArgs[0] == "1" || selectionArgs[0].equals("true", ignoreCase = true)
                rows = rows.filter { it.forwarding == forwarding }
            } else {
                throw IllegalArgumentException("Unsupported app_info selection: $selection")
            }
        }
        val columns = columnsOrDefault(projection, APP_INFO_DEFAULT_COLUMNS, APP_INFO_COLUMNS)
        val cursor = MatrixCursor(columns)
        rows.forEach { app: AppInfo ->
            cursor.addRow(buildRow(columns) { column -> valueFromAppInfo(app, column) })
        }
        return cursor
    }

    private fun queryAppInfoByPackageName(projection: Array<String>?, uri: Uri): Cursor {
        val packageName = uri.lastPathSegment.orEmpty()
        val columns = columnsOrDefault(projection, APP_INFO_DEFAULT_COLUMNS, APP_INFO_COLUMNS)
        val cursor = MatrixCursor(columns)
        if (packageName.isBlank()) {
            return cursor
        }
        val app = db.queryAppInfoByPackageName(packageName)
        if (app != null) {
            cursor.addRow(buildRow(columns) { column -> valueFromAppInfo(app, column) })
        }
        return cursor
    }

    private fun buildRow(columns: Array<String>, resolver: (String) -> Any?): Array<Any?> =
        Array(columns.size) { idx -> resolver(columns[idx]) }

    private fun valueFromSmsCodeRule(rule: SmsCodeRule, column: String): Any? =
        when (column) {
            "_id", "id" -> rule.id
            "company" -> rule.company
            "code_keyword" -> rule.codeKeyword
            "code_regex" -> rule.codeRegex
            else -> null
        }

    private fun valueFromSmsMsg(msg: SmsMsg, column: String): Any? =
        SmsMsgCursorContract.valueFromRecord(msg, column)

    private fun valueFromAppInfo(app: AppInfo, column: String): Any? =
        when (column) {
            "package_name" -> app.packageName
            "label" -> app.label
            "blocked" -> if (app.blocked) 1 else 0
            "forwarding" -> if (app.forwarding) 1 else 0
            "notify_template" -> app.notifyTemplate
            else -> null
        }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        if (!isCallerAllowed()) return 0
        val uriType = uriMatcher.match(uri)
        val rowsUpdated = when (uriType) {
            SMS_MSG_DIR -> updateSmsMsg(values, selection, selectionArgs)
            SMS_MSG_ID -> updateSmsMsgByUriId(uri, values)
            APP_INFO_DIR -> updateAppInfo(values, selection, selectionArgs)
            APP_INFO_ITEM -> updateAppInfoByUri(uri, values)
            else -> 0
        }
        if (rowsUpdated > 0) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        return rowsUpdated
    }

    private fun updateSmsMsgByUriId(uri: Uri, values: ContentValues?): Int {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return 0
        return updateSmsMsgById(id, values)
    }

    private fun updateSmsMsg(values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        if (selection.isNullOrBlank() || selectionArgs.isNullOrEmpty()) {
            return 0
        }
        val normalized = selection.replace("`", "").trim().lowercase()
        if (normalized == "_id = ?" || normalized == "id = ?") {
            val id = selectionArgs.firstOrNull()?.toLongOrNull() ?: return 0
            return updateSmsMsgById(id, values)
        }
        throw IllegalArgumentException("Unsupported sms_msg update selection: $selection")
    }

    private fun updateSmsMsgById(id: Long, values: ContentValues?): Int {
        val existing = db.querySmsMsgById(id) ?: return 0
        val updated = existing.copy(
            sender = values.getBoundedString("sender", MAX_SENDER_LENGTH) ?: existing.sender,
            body = values.getBoundedString("body", MAX_BODY_LENGTH) ?: existing.body,
            date = values.getNonNegativeLong("date", existing.date),
            processedTime = if (values?.containsKey("processed_time") == true) {
                values.getNonNegativeLong("processed_time", 0L)
            } else {
                existing.processedTime
            },
            company = values.getBoundedString("company", MAX_COMPANY_LENGTH) ?: existing.company,
            smsCode = values.getBoundedString("sms_code", MAX_CODE_LENGTH) ?: existing.smsCode,
            packageName = values.getBoundedString("package_name", MAX_PACKAGE_NAME_LENGTH) ?: existing.packageName,
            notifyChannelId = values.getBoundedString("notify_channel_id", MAX_CHANNEL_ID_LENGTH) ?: existing.notifyChannelId,
            simSlot = values.getIntInRange("sim_slot", -1, 1, existing.simSlot),
            subId = values.getNonNegativeInt("sub_id", existing.subId),
            msgType = values.getAllowedInt("msg_type", SMS_MSG_TYPES, existing.msgType),
            callType = values.getAllowedInt("call_type", CALL_TYPES, existing.callType),
            forwardStatus = values.getAllowedInt("forward_status", FORWARD_STATUSES, existing.forwardStatus),
            forwardTarget = values.getBoundedString("forward_target", MAX_FORWARD_TARGET_LENGTH) ?: existing.forwardTarget,
            forwardMessage = values.getBoundedString("forward_message", MAX_BODY_LENGTH) ?: existing.forwardMessage,
            forwardTime = values.getNonNegativeLong("forward_time", existing.forwardTime),
        )
        return db.updateSmsMsg(updated)
    }

    private fun updateAppInfo(values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        if (selection.isNullOrBlank() || selectionArgs.isNullOrEmpty()) {
            return 0
        }
        val normalized = selection.replace("`", "").trim().lowercase()
        if (normalized != "package_name = ?") {
            throw IllegalArgumentException("Unsupported app_info update selection: $selection")
        }
        val packageName = selectionArgs.firstOrNull().orEmpty()
        if (packageName.isBlank()) {
            return 0
        }
        return updateAppInfoByPackageName(packageName, values)
    }

    private fun updateAppInfoByUri(uri: Uri, values: ContentValues?): Int {
        val packageName = uri.lastPathSegment.orEmpty()
        if (packageName.isBlank()) {
            return 0
        }
        return updateAppInfoByPackageName(packageName, values)
    }

    private fun updateAppInfoByPackageName(packageName: String, values: ContentValues?): Int {
        val boundedPackageName = packageName.take(MAX_PACKAGE_NAME_LENGTH)
        val existing = db.queryAppInfoByPackageName(boundedPackageName) ?: AppInfo(packageName = boundedPackageName)
        val blocked = parseBooleanValue(values, "blocked", existing.blocked)
        val forwarding = parseBooleanValue(values, "forwarding", existing.forwarding)
        val label = when {
            values?.containsKey("label") == true -> values.getBoundedString("label", MAX_LABEL_LENGTH)
            else -> existing.label
        }
        val notifyTemplate = when {
            values?.containsKey("notify_template") == true -> values.getBoundedString(
                "notify_template",
                MAX_NOTIFY_TEMPLATE_LENGTH,
            ).orEmpty()
            else -> existing.notifyTemplate
        }
        return db.upsertAppInfo(
            existing.copy(
                label = label,
                blocked = blocked,
                forwarding = forwarding,
                notifyTemplate = notifyTemplate,
            ),
        )
    }

    private fun parseBooleanValue(values: ContentValues?, key: String, defaultValue: Boolean): Boolean {
        if (values?.containsKey(key) != true) return defaultValue
        val raw = values.get(key)
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw == "1" || raw.equals("true", ignoreCase = true)
            else -> defaultValue
        }
    }

    private fun deleteAppInfoByPackageName(uri: Uri): Int {
        val packageName = uri.lastPathSegment.orEmpty()
        if (packageName.isBlank()) {
            return 0
        }
        val app = db.queryAppInfoByPackageName(packageName) ?: return 0
        return db.removeAppInfosByPackage(listOf(app.packageName))
    }

    /**
     * Inserts an auto-input attempt row on behalf of a hook process that cannot
     * open the module's private Room file across UIDs. Field names mirror the
     * [com.github.magisk317.smscode.data.db.entity.AutoInputEvent] columns.
     *
     * `record_id` is optional: the hook side has no provider endpoint to resolve
     * a fingerprint into a record id (see AutoInputAction), so it is typically
     * absent and stored as null.
     */
    private fun addAutoInputEvent(values: ContentValues?): Long {
        val normalized = Contract.normalizeAutoInputEvent(
            id = values?.getAsLong("id"),
            recordId = values?.getAsLong("record_id"),
            packageName = values?.getAsString("package_name"),
            codeLength = values?.getAsInteger("code_length"),
            attemptAt = values?.getAsLong("attempt_at"),
            maxPackageNameLength = MAX_PACKAGE_NAME_LENGTH,
            now = System.currentTimeMillis(),
        )
        return db.insertAutoInputAttempt(
            id = normalized.id,
            recordId = normalized.recordId,
            packageName = normalized.packageName,
            codeLength = normalized.codeLength,
            attemptAt = normalized.attemptAt,
        )
    }

    private val db: DBManager
        get() = mDbManager ?: throw IllegalStateException("DBProvider is not initialized")

    private fun isCallerAllowed(): Boolean {
        val ctx = context ?: return false
        return ProviderCallerGuard.isCallerAllowed(ctx).also { allowed ->
            if (!allowed) {
                XLog.w("DBProvider: deny caller uid=%d", Binder.getCallingUid())
            }
        }
    }

    private fun ContentValues?.toSmsMsg(): SmsMsg = SmsMsg(
        sender = getBoundedString("sender", MAX_SENDER_LENGTH),
        body = getBoundedString("body", MAX_BODY_LENGTH),
        date = getNonNegativeLong("date", 0L),
        processedTime = getNonNegativeLong("processed_time", System.currentTimeMillis()),
        company = getBoundedString("company", MAX_COMPANY_LENGTH),
        smsCode = getBoundedString("sms_code", MAX_CODE_LENGTH),
        packageName = getBoundedString("package_name", MAX_PACKAGE_NAME_LENGTH),
        notifyChannelId = getBoundedString("notify_channel_id", MAX_CHANNEL_ID_LENGTH).orEmpty(),
        simSlot = getIntInRange("sim_slot", -1, 1, -1),
        subId = getNonNegativeInt("sub_id", 0),
        msgType = getAllowedInt("msg_type", SMS_MSG_TYPES, SmsMsg.MSG_TYPE_SMS),
        callType = getAllowedInt("call_type", CALL_TYPES, 0),
        forwardStatus = getAllowedInt("forward_status", FORWARD_STATUSES, SmsMsg.FORWARD_STATUS_NONE),
        forwardTarget = getBoundedString("forward_target", MAX_FORWARD_TARGET_LENGTH),
        forwardMessage = getBoundedString("forward_message", MAX_BODY_LENGTH),
        forwardTime = getNonNegativeLong("forward_time", 0L),
    )

    private fun ContentValues?.getBoundedString(key: String, maxLength: Int): String? {
        val raw = this?.getAsString(key) ?: return null
        return raw.take(maxLength)
    }

    private fun ContentValues?.getNonNegativeLong(key: String, defaultValue: Long): Long {
        val value = this?.getAsLong(key) ?: return defaultValue
        return value.coerceAtLeast(0L)
    }

    private fun ContentValues?.getAllowedInt(key: String, allowedValues: Set<Int>, defaultValue: Int): Int {
        val value = this?.getAsInteger(key) ?: return defaultValue
        return value.takeIf { it in allowedValues } ?: defaultValue
    }

    private fun ContentValues?.getNonNegativeInt(key: String, defaultValue: Int): Int {
        val value = this?.getAsInteger(key) ?: return defaultValue
        return value.coerceAtLeast(0)
    }

    private fun ContentValues?.getIntInRange(key: String, minValue: Int, maxValue: Int, defaultValue: Int): Int {
        val value = this?.getAsInteger(key) ?: return defaultValue
        return value.takeIf { it in minValue..maxValue } ?: defaultValue
    }

    private fun columnsOrDefault(
        projection: Array<String>?,
        defaultColumns: Array<String>,
        allowedColumns: Set<String>,
    ): Array<String> {
        val columns = projection ?: defaultColumns
        val unsupported = columns.filterNot { it in allowedColumns }
        require(unsupported.isEmpty()) {
            "Unsupported projection column(s): ${unsupported.joinToString()}"
        }
        return columns
    }

    private fun parseSortOrder(sortOrder: String?): SortSpec {
        val normalized = sortOrder
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        if (normalized.isEmpty()) return SortSpec(descending = true, limit = null)
        val match = DATE_SORT_REGEX.matchEntire(normalized)
            ?: throw IllegalArgumentException("Unsupported sort order: $sortOrder")
        return SortSpec(
            descending = match.groupValues[1] == "desc",
            limit = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull(),
        )
    }

    private data class SortSpec(
        val descending: Boolean,
        val limit: Int?,
    )

    private data class InsertOutcome(
        val id: Long,
        val inserted: Boolean,
        val duplicate: Boolean = false,
    )

    internal object Contract {
        fun isSupportedDateSortOrder(sortOrder: String?): Boolean {
            return sortOrder
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .orEmpty()
                .let { normalized ->
                    normalized.isEmpty() || DATE_SORT_REGEX.matches(normalized)
                }
        }

        fun isProjectionSupported(projection: Array<String>?, allowedColumns: Set<String>): Boolean {
            return projection == null || projection.all { it in allowedColumns }
        }

        /**
         * Normalizes raw auto_input_event ContentValues into the arguments passed
         * to [DBManager.insertAutoInputAttempt]. Pure so it can be unit tested
         * without an Android runtime.
         *
         * - `id` <= 0 (or null) means "auto-generate", surfaced as null.
         * - `codeLength` is clamped to be non-negative.
         * - `attemptAt` <= 0 (or null) falls back to [now].
         * - `packageName` is truncated to [maxPackageNameLength].
         */
        fun normalizeAutoInputEvent(
            id: Long?,
            recordId: Long?,
            packageName: String?,
            codeLength: Int?,
            attemptAt: Long?,
            maxPackageNameLength: Int,
            now: Long,
        ): AutoInputEventValues {
            return AutoInputEventValues(
                id = id?.takeIf { it > 0L },
                recordId = recordId,
                packageName = packageName?.take(maxPackageNameLength),
                codeLength = (codeLength ?: 0).coerceAtLeast(0),
                attemptAt = attemptAt?.takeIf { it > 0L } ?: now,
            )
        }
    }

    internal data class AutoInputEventValues(
        val id: Long?,
        val recordId: Long?,
        val packageName: String?,
        val codeLength: Int,
        val attemptAt: Long,
    )

    private fun getRetentionDays(ctx: Context): Bundle? {
        val days = runBlocking {
            AppPreferencesDataStore.getInt(
                ctx,
                PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                PrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT,
            )
        }.coerceAtLeast(PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN)
        return Bundle().apply {
            putInt(RuntimeStateProviderContract.RESULT_RETENTION_DAYS, days)
        }
    }

    private fun trimOldRecordsIfNeeded(ctx: Context, smsMsg: SmsMsg) {
        val isCodeSms = !smsMsg.smsCode.isNullOrBlank()
        val limitKey = when (smsMsg.msgType) {
            SmsMsg.MSG_TYPE_APP_NOTIFY -> PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY
            SmsMsg.MSG_TYPE_CALL_NOTIFY -> PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY
            SmsMsg.MSG_TYPE_SMS -> if (isCodeSms) PrefConst.KEY_HISTORY_LIMIT_CODE else PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS
            else -> PrefConst.KEY_HISTORY_LIMIT_CODE
        }
        val limit = runBlocking {
            AppPreferencesDataStore.getString(ctx, limitKey, "0")
        }.toIntOrNull() ?: 0
        if (limit <= 0) return

        val all = db.queryAllSmsMsg()
        val matching = all.asSequence()
            .filter { record ->
                record.msgType == smsMsg.msgType &&
                    (smsMsg.msgType != SmsMsg.MSG_TYPE_SMS || (!record.smsCode.isNullOrBlank()) == isCodeSms)
            }
            .sortedBy { it.date }
            .toList()
        if (matching.size < limit) return
        val deleteCount = matching.size - limit + 1
        db.removeSmsMsgList(matching.take(deleteCount))
    }

    companion object {
        private const val PATH_SMS_MSG = "sms_msg"
        private const val PATH_SMS_CODE_RULE = "sms_code_rule"
        private const val PATH_APP_INFO = "app_info"
        private const val PATH_AUTO_INPUT_EVENT = "auto_input_event"
        private const val KEY_DEDUPLICATE = "deduplicate"
        const val QUERY_DUPLICATE = "duplicate"
        private const val SMS_MSG_DIR = 0
        private const val SMS_MSG_ID = 1
        private const val SMS_CODE_RULE_DIR = 2
        private const val SMS_CODE_RULE_ID = 3
        private const val APP_INFO_DIR = 4
        private const val APP_INFO_ITEM = 5
        private const val AUTO_INPUT_EVENT_DIR = 6
        private const val MAX_SENDER_LENGTH = 128
        private const val MAX_BODY_LENGTH = 4096
        private const val MAX_COMPANY_LENGTH = 128
        private const val MAX_CODE_LENGTH = 64
        private const val MAX_PACKAGE_NAME_LENGTH = 255
        private const val MAX_CHANNEL_ID_LENGTH = 255
        private const val MAX_FORWARD_TARGET_LENGTH = 512
        private const val MAX_LABEL_LENGTH = 256
        private const val MAX_NOTIFY_TEMPLATE_LENGTH = 2048
        private const val MAX_RUNTIME_GATE_ENTRIES = 4096

        internal val SMS_MSG_COLUMNS = SmsMsgCursorContract.defaultColumns.toSet() + "id"
        internal val SMS_CODE_RULE_COLUMNS = setOf("_id", "id", "company", "code_keyword", "code_regex")
        internal val APP_INFO_COLUMNS = setOf("package_name", "label", "blocked", "forwarding", "notify_template")
        private val SMS_CODE_RULE_DEFAULT_COLUMNS = arrayOf("company", "code_keyword", "code_regex", "_id")
        private val APP_INFO_DEFAULT_COLUMNS = arrayOf("package_name", "label", "blocked", "forwarding", "notify_template")
        private val SMS_MSG_TYPES = setOf(SmsMsg.MSG_TYPE_SMS, SmsMsg.MSG_TYPE_APP_NOTIFY, SmsMsg.MSG_TYPE_CALL_NOTIFY)
        private val FORWARD_STATUSES = setOf(
            SmsMsg.FORWARD_STATUS_NONE,
            SmsMsg.FORWARD_STATUS_SUCCESS,
            SmsMsg.FORWARD_STATUS_FAILED,
            SmsMsg.FORWARD_STATUS_PARTIAL,
            SmsMsg.FORWARD_STATUS_BLOCKED,
        )
        private val CALL_TYPES = (0..6).toSet()
        private val DATE_SORT_REGEX = Regex("""date (asc|desc)(?: limit ([1-9]\d{0,3}))?""")

        fun authority(context: Context): String = "${context.packageName}.db.provider"

        fun smsMsgContentUri(context: Context): Uri =
            "content://${context.packageName}.db.provider/$PATH_SMS_MSG".toUri()

        fun smsCodeRuleContentUri(context: Context): Uri =
            "content://${context.packageName}.db.provider/$PATH_SMS_CODE_RULE".toUri()

        fun appInfoContentUri(context: Context): Uri =
            "content://${context.packageName}.db.provider/$PATH_APP_INFO".toUri()

        fun autoInputEventContentUri(context: Context): Uri =
            "content://${context.packageName}.db.provider/$PATH_AUTO_INPUT_EVENT".toUri()

        private fun runtimeStateContentUri(context: Context): Uri =
            "content://${authority(context)}".toUri()

        fun claimRuntimeGate(
            context: Context,
            fileName: String,
            keys: Collection<String>,
            windowMs: Long,
            maxEntries: Int = RuntimeStateProviderContract.DEFAULT_MAX_ENTRIES,
        ): HookRuntimeGateClaimResult {
            val extras = Bundle().apply {
                putStringArrayList(RuntimeStateProviderContract.EXTRA_KEYS, ArrayList(keys))
                putLong(RuntimeStateProviderContract.EXTRA_WINDOW_MS, windowMs)
                putInt(RuntimeStateProviderContract.EXTRA_MAX_ENTRIES, maxEntries)
            }
            val result = runCatching {
                context.contentResolver.call(
                    runtimeStateContentUri(context),
                    RuntimeStateProviderContract.METHOD_CLAIM_RUNTIME_GATE,
                    fileName,
                    extras,
                )
            }.getOrNull()
            val age = result?.getLong(
                RuntimeStateProviderContract.RESULT_AGE_MS,
                RuntimeStateProviderContract.NO_AGE_MS,
            ) ?: RuntimeStateProviderContract.NO_AGE_MS
            return HookRuntimeGateClaimResult(
                claimed = result?.getBoolean(RuntimeStateProviderContract.RESULT_CLAIMED, true) ?: true,
                ageMs = age.takeIf { it >= 0L },
                blockedKey = result?.getString(RuntimeStateProviderContract.RESULT_BLOCKED_KEY),
            )
        }

        fun recordHookHeartbeat(
            context: Context,
            packageName: String,
            processName: String,
            source: String,
            verboseLogging: Boolean,
            route: String,
        ): Boolean {
            val extras = Bundle().apply {
                putString(RuntimeStateProviderContract.EXTRA_PACKAGE_NAME, packageName)
                putString(RuntimeStateProviderContract.EXTRA_PROCESS_NAME, processName)
                putString(RuntimeStateProviderContract.EXTRA_SOURCE, source)
                putBoolean(RuntimeStateProviderContract.EXTRA_VERBOSE_LOGGING, verboseLogging)
                putString(RuntimeStateProviderContract.EXTRA_ROUTE, route)
            }
            return runCatching {
                context.contentResolver.call(
                    runtimeStateContentUri(context),
                    RuntimeStateProviderContract.METHOD_RECORD_HOOK_HEARTBEAT,
                    null,
                    extras,
                )?.getBoolean(RuntimeStateProviderContract.RESULT_OK, false) == true
            }.getOrDefault(false)
        }

        /**
         * Notify-only signal URI for hook-side prefs cache invalidation.
         * Not backed by a table: writers call [notifyPrefsCacheChanged]; the hook process
         * registers a ContentObserver and clears [com.github.magisk317.smscode.common.utils.HookPrefsReader].
         */
        fun prefsCacheContentUri(context: Context): Uri =
            prefsCacheContentUriString(context.packageName).toUri()

        /**
         * Notify-only signal URI for hook-side rule caches (user + official snapshot).
         * Writers call [notifyRulesCacheChanged] after Room rule mutations or official refresh.
         */
        fun rulesCacheContentUri(context: Context): Uri =
            rulesCacheContentUriString(context.packageName).toUri()

        /** Pure string form of [prefsCacheContentUri] for unit tests (no Android Uri.parse). */
        fun prefsCacheContentUriString(packageName: String): String =
            "content://$packageName.db.provider/$PATH_PREFS_CACHE"

        /** Pure string form of [rulesCacheContentUri] for unit tests (no Android Uri.parse). */
        fun rulesCacheContentUriString(packageName: String): String =
            "content://$packageName.db.provider/$PATH_RULES_CACHE"

        internal const val PATH_PREFS_CACHE = "prefs_cache"
        internal const val PATH_RULES_CACHE = "rules_cache"

        /**
         * Broadcasts a prefs-cache invalidation to any hooked process observing
         * [prefsCacheContentUri]. Safe to call from the module app after mirroring
         * settings to the Xposed RemotePreferences group.
         */
        fun notifyPrefsCacheChanged(context: Context) {
            val appContext = context.applicationContext ?: context
            runCatching {
                appContext.contentResolver.notifyChange(prefsCacheContentUri(appContext), null)
            }.onFailure { error ->
                XLog.w(
                    "notifyPrefsCacheChanged failed: %s",
                    error.message ?: error.javaClass.simpleName,
                )
            }
        }

        /**
         * Broadcasts a rules-cache invalidation to any hooked process observing
         * [rulesCacheContentUri]. Safe to call after user-rule CRUD or official refresh.
         */
        fun notifyRulesCacheChanged(context: Context) {
            val appContext = context.applicationContext ?: context
            runCatching {
                appContext.contentResolver.notifyChange(rulesCacheContentUri(appContext), null)
            }.onFailure { error ->
                XLog.w(
                    "notifyRulesCacheChanged failed: %s",
                    error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }
}

package com.tianma.xsmscode.web

import android.content.Context
import android.content.pm.PackageManager
import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.forwarder.utils.SenderType
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import com.tianma.xsmscode.data.db.AppDatabase
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.data.db.entity.SmsMsg
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class WebUiServer(
    context: Context,
    private val allowLanAccess: Boolean,
) {

    private val appContext = context.applicationContext ?: context
    private val database by lazy { AppDatabase.getInstance(appContext) }
    private val host = if (allowLanAccess) HOST_LAN else HOST_LOCAL

    private var engine: EmbeddedServer<*, *>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun start() {
        if (engine != null) return
        engine = embeddedServer(
            factory = CIO,
            host = host,
            port = PORT,
        ) {
            configureRoutes()
        }.start(wait = false)
        Timber.i("WebUI server started at http://%s:%d (lan=%s)", host, PORT, allowLanAccess)
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        engine = null
        Timber.i("WebUI server stopped")
    }

    @Suppress("CyclomaticComplexMethod")
    private fun Application.configureRoutes() {
        routing {
            get("/") {
                call.respondText(
                    contentType = ContentType.Text.Html,
                    text = WEB_INDEX,
                )
            }

            get("/health") {
                call.respondText(
                    text = json.encodeToString(HealthResponse(status = "ok")),
                    contentType = ContentType.Application.Json,
                )
            }

            get("/api/apps") {
                val apps = loadMergedAppItems()
                call.respondText(
                    text = json.encodeToString(apps),
                    contentType = ContentType.Application.Json,
                )
            }

            get("/api/records") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
                val records = withContext(Dispatchers.IO) {
                    database.smsMsgDao().getAll()
                        .take(limit)
                        .map { it.toRecordItem() }
                }
                call.respondText(
                    text = json.encodeToString(records),
                    contentType = ContentType.Application.Json,
                )
            }

            post("/api/records/{recordId}/delete") {
                val recordId = call.parameters["recordId"]?.toLongOrNull()
                if (recordId == null) {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "Invalid recordId")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }

                val deleted = withContext(Dispatchers.IO) {
                    val dao = database.smsMsgDao()
                    val existing = dao.getById(recordId) ?: return@withContext false
                    dao.delete(existing)
                    true
                }

                if (!deleted) {
                    call.respondText(
                        status = HttpStatusCode.NotFound,
                        text = json.encodeToString(ErrorResponse(error = "Record not found")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }

                call.respondText(
                    text = json.encodeToString(SimpleOkResponse(ok = true)),
                    contentType = ContentType.Application.Json,
                )
            }

            get("/api/advanced") {
                call.respondText(
                    text = json.encodeToString(buildAdvancedState()),
                    contentType = ContentType.Application.Json,
                )
            }

            post("/api/advanced") {
                val payload = runCatching {
                    json.decodeFromString<AdvancedUpdatePayload>(call.receiveText())
                }.getOrElse {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "Invalid JSON payload")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }
                if (payload.enableSmsBlacklist == null && payload.webUiLanAccess == null) {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "No changes provided")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }
                withContext(Dispatchers.IO) {
                    payload.enableSmsBlacklist?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_ENABLE_SMS_BLACKLIST, it)
                    }
                    payload.webUiLanAccess?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_WEBUI_LAN_ACCESS, it)
                    }
                }
                call.respondText(
                    text = json.encodeToString(buildAdvancedState()),
                    contentType = ContentType.Application.Json,
                )
            }

            get("/api/settings") {
                call.respondText(
                    text = json.encodeToString(buildSettingsState()),
                    contentType = ContentType.Application.Json,
                )
            }

            post("/api/settings") {
                val payload = runCatching {
                    json.decodeFromString<SettingsUpdatePayload>(call.receiveText())
                }.getOrElse {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "Invalid JSON payload")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }
                if (
                    payload.enable == null &&
                    payload.copyToClipboard == null &&
                    payload.showToast == null &&
                    payload.showCodeNotification == null &&
                    payload.enableAutoInputCode == null &&
                    payload.enableAutoEnterCode == null &&
                    payload.verboseLogMode == null &&
                    payload.deduplicateSms == null &&
                    payload.blockSms == null &&
                    payload.enableCodeRecords == null &&
                    payload.forceStopRecovery == null
                ) {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "No changes provided")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }
                withContext(Dispatchers.IO) {
                    payload.enable?.let { AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_ENABLE, it) }
                    payload.copyToClipboard?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_COPY_TO_CLIPBOARD, it)
                    }
                    payload.showToast?.let { AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_SHOW_TOAST, it) }
                    payload.showCodeNotification?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_SHOW_CODE_NOTIFICATION, it)
                    }
                    payload.enableAutoInputCode?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, it)
                    }
                    payload.enableAutoEnterCode?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, it)
                    }
                    payload.verboseLogMode?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_VERBOSE_LOG_MODE, it)
                    }
                    payload.deduplicateSms?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_DEDUPLICATE_SMS, it)
                    }
                    payload.blockSms?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_BLOCK_SMS, it)
                    }
                    payload.enableCodeRecords?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_ENABLE_CODE_RECORDS, it)
                    }
                    payload.forceStopRecovery?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_FORCE_STOP_RECOVERY, it)
                    }
                }
                call.respondText(
                    text = json.encodeToString(buildSettingsState()),
                    contentType = ContentType.Application.Json,
                )
            }

            get("/api/intercept") {
                call.respondText(
                    text = json.encodeToString(buildInterceptState()),
                    contentType = ContentType.Application.Json,
                )
            }

            post("/api/intercept") {
                val payload = runCatching {
                    json.decodeFromString<InterceptUpdatePayload>(call.receiveText())
                }.getOrElse {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "Invalid JSON payload")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }
                if (
                    payload.smsBlacklistNumbers == null &&
                    payload.smsBlacklistPrefixes == null &&
                    payload.smsBlacklistRegex == null &&
                    payload.smsBlacklistContent == null &&
                    payload.smsBlacklistActionDelete == null &&
                    payload.smsBlacklistActionBlock == null
                ) {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "No changes provided")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }
                withContext(Dispatchers.IO) {
                    payload.smsBlacklistNumbers?.let {
                        AppPreferencesDataStore.setString(appContext, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, it)
                    }
                    payload.smsBlacklistPrefixes?.let {
                        AppPreferencesDataStore.setString(appContext, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, it)
                    }
                    payload.smsBlacklistRegex?.let {
                        AppPreferencesDataStore.setString(appContext, PrefConst.KEY_SMS_BLACKLIST_REGEX, it)
                    }
                    payload.smsBlacklistContent?.let {
                        AppPreferencesDataStore.setString(appContext, PrefConst.KEY_SMS_BLACKLIST_CONTENT, it)
                    }
                    payload.smsBlacklistActionDelete?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, it)
                    }
                    payload.smsBlacklistActionBlock?.let {
                        AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, it)
                    }
                }
                call.respondText(
                    text = json.encodeToString(buildInterceptState()),
                    contentType = ContentType.Application.Json,
                )
            }

            get("/api/senders") {
                call.respondText(
                    text = json.encodeToString(buildSenderItems()),
                    contentType = ContentType.Application.Json,
                )
            }

            post("/api/senders") {
                val payload = runCatching {
                    json.decodeFromString<SenderCreatePayload>(call.receiveText())
                }.getOrElse {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "Invalid JSON payload")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }
                val name = payload.name.trim()
                if (name.isBlank()) {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "Sender name is required")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }
                val created = withContext(Dispatchers.IO) {
                    val dao = database.senderDao()
                    val newSender = Sender(
                        id = 0L,
                        type = payload.type,
                        name = name,
                        jsonSetting = payload.jsonSetting,
                        status = if (payload.status) 1 else 0,
                        receiveCode = if (payload.receiveCode) 1 else 0,
                        receiveNonCode = if (payload.receiveNonCode) 1 else 0,
                        receiveAppNotify = if (payload.receiveAppNotify) 1 else 0,
                    )
                    val insertedId = dao.insert(newSender)
                    dao.getOne(insertedId)?.toSenderItem()
                }
                if (created == null) {
                    call.respondText(
                        status = HttpStatusCode.InternalServerError,
                        text = json.encodeToString(ErrorResponse(error = "Failed to create sender")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }
                call.respondText(
                    text = json.encodeToString(created),
                    contentType = ContentType.Application.Json,
                )
            }

            post("/api/senders/{senderId}") {
                val senderId = call.parameters["senderId"]?.toLongOrNull()
                if (senderId == null) {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "Invalid senderId")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }

                val payload = runCatching {
                    json.decodeFromString<SenderUpdatePayload>(call.receiveText())
                }.getOrElse {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "Invalid JSON payload")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }
                val trimmedName = payload.name?.trim()
                if (payload.name != null && trimmedName.isNullOrBlank()) {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "Sender name is required")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }

                if (
                    payload.name == null &&
                    payload.type == null &&
                    payload.jsonSetting == null &&
                    payload.status == null &&
                    payload.receiveCode == null &&
                    payload.receiveNonCode == null &&
                    payload.receiveAppNotify == null
                ) {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "No changes provided")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }

                val updated = withContext(Dispatchers.IO) {
                    val dao = database.senderDao()
                    val current = dao.getOne(senderId) ?: return@withContext null
                    trimmedName?.let { current.name = it }
                    payload.type?.let { current.type = it }
                    payload.jsonSetting?.let { current.jsonSetting = it }
                    current.status = if (payload.status ?: (current.status == 1)) 1 else 0
                    current.receiveCode = if (payload.receiveCode ?: (current.receiveCode == 1)) 1 else 0
                    current.receiveNonCode = if (payload.receiveNonCode ?: (current.receiveNonCode == 1)) 1 else 0
                    current.receiveAppNotify = if (payload.receiveAppNotify ?: (current.receiveAppNotify == 1)) 1 else 0
                    dao.update(current)
                    current.toSenderItem()
                }

                if (updated == null) {
                    call.respondText(
                        status = HttpStatusCode.NotFound,
                        text = json.encodeToString(ErrorResponse(error = "Sender not found")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }

                call.respondText(
                    text = json.encodeToString(updated),
                    contentType = ContentType.Application.Json,
                )
            }

            post("/api/senders/{senderId}/delete") {
                val senderId = call.parameters["senderId"]?.toLongOrNull()
                if (senderId == null) {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "Invalid senderId")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }
                val deleted = withContext(Dispatchers.IO) {
                    val dao = database.senderDao()
                    val existing = dao.getOne(senderId) ?: return@withContext false
                    dao.delete(existing)
                    true
                }
                if (!deleted) {
                    call.respondText(
                        status = HttpStatusCode.NotFound,
                        text = json.encodeToString(ErrorResponse(error = "Sender not found")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }
                call.respondText(
                    text = json.encodeToString(SimpleOkResponse(ok = true)),
                    contentType = ContentType.Application.Json,
                )
            }

            post("/api/apps/{packageName}") {
                val packageName = call.parameters["packageName"]?.trim().orEmpty()
                if (packageName.isBlank()) {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "Missing packageName")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }

                val payload = runCatching {
                    json.decodeFromString<AppUpdatePayload>(call.receiveText())
                }.getOrElse {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "Invalid JSON payload")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }

                if (payload.blocked == null && payload.forwarding == null && payload.notifyTemplate == null) {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = json.encodeToString(ErrorResponse(error = "No changes provided")),
                        contentType = ContentType.Application.Json,
                    )
                    return@post
                }

                val updated = withContext(Dispatchers.IO) {
                    val dao = database.appInfoDao()
                    val current = dao.getByPackageName(packageName)
                    val label = current?.label?.takeIf { it.isNotBlank() }
                        ?: resolveInstalledAppLabel(packageName)
                        ?: packageName
                    val next = (current ?: AppInfo(packageName = packageName, label = label)).copy(
                        label = label,
                        blocked = payload.blocked ?: current?.blocked ?: false,
                        forwarding = payload.forwarding ?: current?.forwarding ?: false,
                        notifyTemplate = payload.notifyTemplate ?: current?.notifyTemplate.orEmpty(),
                    )
                    dao.insert(next)
                    next.toItem()
                }

                call.respondText(
                    text = json.encodeToString(updated),
                    contentType = ContentType.Application.Json,
                )
            }
        }
    }

    private suspend fun loadMergedAppItems(): List<AppItem> = withContext(Dispatchers.IO) {
        val dao = database.appInfoDao()
        val configMap = dao.getAll().associateBy { it.packageName }
        val packageManager = appContext.packageManager
        packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
            .asSequence()
            .map { appInfo ->
                val packageName = appInfo.packageName
                val label = runCatching {
                    packageManager.getApplicationLabel(appInfo).toString()
                }.getOrDefault(packageName).ifBlank { packageName }
                val config = configMap[packageName]
                AppItem(
                    packageName = packageName,
                    label = label,
                    blocked = config?.blocked ?: false,
                    forwarding = config?.forwarding ?: false,
                    notifyTemplate = config?.notifyTemplate.orEmpty(),
                )
            }
            .sortedWith(
                compareByDescending<AppItem> { it.blocked || it.forwarding || it.notifyTemplate.isNotBlank() }
                    .thenBy { it.label.lowercase() },
            )
            .toList()
    }

    private fun resolveInstalledAppLabel(packageName: String): String? {
        val packageManager = appContext.packageManager
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_ALL)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun AppInfo.toItem(): AppItem = AppItem(
        packageName = packageName,
        label = label?.takeIf { it.isNotBlank() } ?: packageName,
        blocked = blocked,
        forwarding = forwarding,
        notifyTemplate = notifyTemplate,
    )

    private fun SmsMsg.toRecordItem(): RecordItem = RecordItem(
        id = id ?: -1,
        date = date,
        sender = sender.orEmpty(),
        body = body.orEmpty(),
        smsCode = smsCode.orEmpty(),
        packageName = packageName.orEmpty(),
        msgType = msgType,
        forwardStatus = forwardStatus,
        forwardTarget = forwardTarget.orEmpty(),
        forwardMessage = forwardMessage.orEmpty(),
    )

    private suspend fun buildAdvancedState(): AdvancedState = withContext(Dispatchers.IO) {
        val senders = database.senderDao().getAll()
        AdvancedState(
            enableSmsBlacklist = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_ENABLE_SMS_BLACKLIST,
                false,
            ),
            webUiLanAccess = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_WEBUI_LAN_ACCESS,
                false,
            ),
            senderTotal = senders.size,
            senderEnabled = senders.count { it.status == 1 },
            senderAppNotifyEnabled = senders.count { it.status == 1 && it.receiveAppNotify == 1 },
        )
    }

    private suspend fun buildSettingsState(): SettingsState = withContext(Dispatchers.IO) {
        SettingsState(
            enable = AppPreferencesDataStore.getBoolean(appContext, PrefConst.KEY_ENABLE, true),
            copyToClipboard = AppPreferencesDataStore.getBoolean(appContext, PrefConst.KEY_COPY_TO_CLIPBOARD, false),
            showToast = AppPreferencesDataStore.getBoolean(appContext, PrefConst.KEY_SHOW_TOAST, true),
            showCodeNotification = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                true,
            ),
            enableAutoInputCode = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
                true,
            ),
            enableAutoEnterCode = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
                false,
            ),
            verboseLogMode = AppPreferencesDataStore.getBoolean(appContext, PrefConst.KEY_VERBOSE_LOG_MODE, false),
            deduplicateSms = AppPreferencesDataStore.getBoolean(appContext, PrefConst.KEY_DEDUPLICATE_SMS, true),
            blockSms = AppPreferencesDataStore.getBoolean(appContext, PrefConst.KEY_BLOCK_SMS, false),
            enableCodeRecords = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_ENABLE_CODE_RECORDS,
                true,
            ),
            forceStopRecovery = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_FORCE_STOP_RECOVERY,
                false,
            ),
        )
    }

    private suspend fun buildInterceptState(): InterceptState = withContext(Dispatchers.IO) {
        InterceptState(
            smsBlacklistNumbers = AppPreferencesDataStore.getString(appContext, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, ""),
            smsBlacklistPrefixes = AppPreferencesDataStore.getString(
                appContext,
                PrefConst.KEY_SMS_BLACKLIST_PREFIXES,
                "",
            ),
            smsBlacklistRegex = AppPreferencesDataStore.getString(appContext, PrefConst.KEY_SMS_BLACKLIST_REGEX, ""),
            smsBlacklistContent = AppPreferencesDataStore.getString(appContext, PrefConst.KEY_SMS_BLACKLIST_CONTENT, ""),
            smsBlacklistActionDelete = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
                true,
            ),
            smsBlacklistActionBlock = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
                false,
            ),
        )
    }

    private suspend fun buildSenderItems(): List<SenderItem> = withContext(Dispatchers.IO) {
        database.senderDao().getAll()
            .map { it.toSenderItem() }
    }

    private fun Sender.toSenderItem(): SenderItem = SenderItem(
        id = id,
        name = name,
        type = type,
        typeLabel = senderTypeLabel(type),
        jsonSetting = jsonSetting,
        status = status == 1,
        receiveCode = receiveCode == 1,
        receiveNonCode = receiveNonCode == 1,
        receiveAppNotify = receiveAppNotify == 1,
    )

    private fun senderTypeLabel(type: Int): String = when (type) {
        SenderType.DINGTALK_GROUP_ROBOT -> "钉钉群机器人"
        SenderType.EMAIL -> "邮件"
        SenderType.BARK -> "Bark"
        SenderType.WEBHOOK -> "Webhook"
        SenderType.WEWORK_ROBOT -> "企业微信机器人"
        SenderType.WEWORK_AGENT -> "企业微信应用"
        SenderType.SERVERCHAN -> "Server酱"
        SenderType.TELEGRAM -> "Telegram"
        SenderType.SMS -> "短信"
        SenderType.FEISHU -> "飞书"
        SenderType.PUSHPLUS -> "PushPlus"
        SenderType.GOTIFY -> "Gotify"
        SenderType.DINGTALK_INNER_ROBOT -> "钉钉内部机器人"
        SenderType.FEISHU_APP -> "飞书应用"
        SenderType.URL_SCHEME -> "URL Scheme"
        SenderType.SOCKET -> "Socket"
        else -> "Unknown($type)"
    }

    @Serializable
    private data class HealthResponse(val status: String)

    @Serializable
    private data class ErrorResponse(val error: String)

    @Serializable
    private data class SimpleOkResponse(val ok: Boolean)

    @Serializable
    data class AppUpdatePayload(
        val blocked: Boolean? = null,
        val forwarding: Boolean? = null,
        val notifyTemplate: String? = null,
    )

    @Serializable
    data class AdvancedState(
        val enableSmsBlacklist: Boolean,
        val webUiLanAccess: Boolean,
        val senderTotal: Int,
        val senderEnabled: Int,
        val senderAppNotifyEnabled: Int,
    )

    @Serializable
    data class AdvancedUpdatePayload(
        val enableSmsBlacklist: Boolean? = null,
        val webUiLanAccess: Boolean? = null,
    )

    @Serializable
    data class SettingsState(
        val enable: Boolean,
        val copyToClipboard: Boolean,
        val showToast: Boolean,
        val showCodeNotification: Boolean,
        val enableAutoInputCode: Boolean,
        val enableAutoEnterCode: Boolean,
        val verboseLogMode: Boolean,
        val deduplicateSms: Boolean,
        val blockSms: Boolean,
        val enableCodeRecords: Boolean,
        val forceStopRecovery: Boolean,
    )

    @Serializable
    data class SettingsUpdatePayload(
        val enable: Boolean? = null,
        val copyToClipboard: Boolean? = null,
        val showToast: Boolean? = null,
        val showCodeNotification: Boolean? = null,
        val enableAutoInputCode: Boolean? = null,
        val enableAutoEnterCode: Boolean? = null,
        val verboseLogMode: Boolean? = null,
        val deduplicateSms: Boolean? = null,
        val blockSms: Boolean? = null,
        val enableCodeRecords: Boolean? = null,
        val forceStopRecovery: Boolean? = null,
    )

    @Serializable
    data class InterceptState(
        val smsBlacklistNumbers: String,
        val smsBlacklistPrefixes: String,
        val smsBlacklistRegex: String,
        val smsBlacklistContent: String,
        val smsBlacklistActionDelete: Boolean,
        val smsBlacklistActionBlock: Boolean,
    )

    @Serializable
    data class InterceptUpdatePayload(
        val smsBlacklistNumbers: String? = null,
        val smsBlacklistPrefixes: String? = null,
        val smsBlacklistRegex: String? = null,
        val smsBlacklistContent: String? = null,
        val smsBlacklistActionDelete: Boolean? = null,
        val smsBlacklistActionBlock: Boolean? = null,
    )

    @Serializable
    data class SenderItem(
        val id: Long,
        val name: String,
        val type: Int,
        val typeLabel: String,
        val jsonSetting: String,
        val status: Boolean,
        val receiveCode: Boolean,
        val receiveNonCode: Boolean,
        val receiveAppNotify: Boolean,
    )

    @Serializable
    data class SenderCreatePayload(
        val name: String = "",
        val type: Int = SenderType.WEBHOOK,
        val jsonSetting: String = "",
        val status: Boolean = true,
        val receiveCode: Boolean = true,
        val receiveNonCode: Boolean = true,
        val receiveAppNotify: Boolean = true,
    )

    @Serializable
    data class SenderUpdatePayload(
        val name: String? = null,
        val type: Int? = null,
        val jsonSetting: String? = null,
        val status: Boolean? = null,
        val receiveCode: Boolean? = null,
        val receiveNonCode: Boolean? = null,
        val receiveAppNotify: Boolean? = null,
    )

    @Serializable
    data class AppItem(
        val packageName: String,
        val label: String,
        val blocked: Boolean,
        val forwarding: Boolean,
        val notifyTemplate: String,
    )

    @Serializable
    data class RecordItem(
        val id: Long,
        val date: Long,
        val sender: String,
        val body: String,
        val smsCode: String,
        val packageName: String,
        val msgType: Int,
        val forwardStatus: Int,
        val forwardTarget: String,
        val forwardMessage: String,
    )

    companion object {
        private const val HOST_LOCAL = "127.0.0.1"
        private const val HOST_LAN = "0.0.0.0"
        private const val PORT = 8787

        private val WEB_INDEX = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>XSmsCode WebUI</title>
              <style>
                body { font-family: "Noto Sans SC", "PingFang SC", sans-serif; margin: 0; background: #f5f7fb; color: #111827; }
                .wrap { max-width: 1080px; margin: 0 auto; padding: 16px; }
                .card { background: #fff; border-radius: 12px; padding: 16px; margin-bottom: 14px; box-shadow: 0 4px 20px rgba(15,23,42,.08); }
                h1, h2 { margin: 0 0 12px; }
                h3 { margin: 0; }
                .muted { color: #6b7280; font-size: 13px; }
                .toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; }
                .btn { border: 0; background: #2563eb; color: #fff; padding: 8px 12px; border-radius: 8px; cursor: pointer; }
                .btn:disabled { opacity: .5; cursor: not-allowed; }
                .tabs { display: flex; gap: 8px; flex-wrap: wrap; }
                .tab-btn { border: 1px solid #d1d5db; background: #fff; color: #111827; padding: 8px 12px; border-radius: 999px; cursor: pointer; }
                .tab-btn.active { background: #2563eb; border-color: #2563eb; color: #fff; }
                .tab-panel { display: none; }
                .tab-panel.active { display: block; }
                .stats-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
                .stat { background: #f8fafc; border-radius: 10px; padding: 12px; }
                .stat .n { font-size: 24px; font-weight: 700; }
                .stat .l { color: #64748b; font-size: 12px; }
                table { width: 100%; border-collapse: collapse; font-size: 14px; }
                th, td { border-bottom: 1px solid #eef2f7; text-align: left; padding: 10px 8px; vertical-align: top; }
                input[type=text] { width: 100%; padding: 6px 8px; border: 1px solid #d1d5db; border-radius: 8px; }
                .record-desktop { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:10px; align-items:start; }
                .record-col { min-width:0; border:1px solid #eef2f7; border-radius:10px; background:#fff; padding:10px; }
                .record-col-title { font-weight:600; margin-bottom:10px; }
                .record-mobile { display:none; }
                .record-tabs { display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap; }
                .record-tab { border:1px solid #d1d5db; border-radius:999px; background:#fff; padding:6px 12px; cursor:pointer; }
                .record-tab.active { background:#2563eb; border-color:#2563eb; color:#fff; }
                .record-list { display:flex; flex-direction:column; gap:10px; }
                .record-card { border:1px solid #e5e7eb; border-radius:10px; background:#f8fafc; padding:10px 12px; }
                .record-card .top { display:flex; justify-content:space-between; gap:8px; align-items:center; margin-bottom:6px; }
                .record-card .top-right { display:flex; align-items:center; gap:8px; }
                .record-card .sender { font-weight:600; font-size:14px; }
                .record-card .time { color:#64748b; font-size:12px; }
                .record-delete-btn { opacity:0; pointer-events:none; transition:opacity .15s ease; }
                .record-card:hover .record-delete-btn { opacity:1; pointer-events:auto; }
                .record-card .body { font-size:13px; white-space:pre-wrap; word-break:break-word; }
                .record-card .meta { margin-top:8px; color:#64748b; font-size:12px; display:flex; gap:12px; flex-wrap:wrap; }
                .record-card .meta .forward-detail { white-space:pre-line; flex-basis:100%; }
                .badge { display:inline-block; background:#e2e8f0; color:#334155; border-radius:999px; padding:2px 8px; font-size:12px; }
                .switch-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
                .switch-row { display: flex; justify-content: space-between; gap: 8px; align-items: center; padding: 10px 12px; border: 1px solid #e5e7eb; border-radius: 10px; background: #f8fafc; }
                .switch-row .t { font-size: 14px; }
                .settings-groups { display:flex; flex-direction:column; gap:10px; }
                .settings-group { border:1px solid #e5e7eb; border-radius:10px; background:#fff; padding:10px; }
                .settings-group-title { font-size:14px; font-weight:600; margin:0 0 8px; color:#374151; }
                .kpi { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
                .kpi .item { background: #f8fafc; border-radius: 10px; padding: 10px 12px; }
                .kpi .item .n { font-size: 18px; font-weight: 700; }
                .kpi .item .l { color: #64748b; font-size: 12px; }
                .row-actions { display:flex; gap:6px; }
                .mini-btn { border:1px solid #d1d5db; background:#fff; border-radius:6px; padding:4px 8px; cursor:pointer; font-size:12px; }
                .mini-btn.danger { border-color:#ef4444; color:#b91c1c; }
                .sender-editor { border:1px solid #e5e7eb; border-radius:10px; background:#f8fafc; padding:10px 12px; margin-bottom:10px; }
                .sender-editor .grid { display:grid; grid-template-columns: 1fr 1fr; gap:10px; }
                .sender-editor textarea, .sender-editor input, .sender-editor select { width:100%; border:1px solid #d1d5db; border-radius:8px; padding:6px 8px; box-sizing:border-box; }
                @media (max-width: 900px) {
                  .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                  .record-desktop { display:none; }
                  .record-mobile { display:block; }
                  .switch-grid { grid-template-columns: 1fr; }
                  .kpi { grid-template-columns: 1fr; }
                  .sender-editor .grid { grid-template-columns: 1fr; }
                }
                @media (hover: none), (pointer: coarse) {
                  .record-delete-btn { opacity:1; pointer-events:auto; }
                }
              </style>
            </head>
            <body>
              <div class="wrap">
                <div class="card">
                  <h1>XSmsCode WebUI</h1>
                  <div class="muted">识别短信验证码并自动复制/自动输入，支持短信与应用通知转发。</div>
                  <div class="muted"><a href="https://github.com/magisk317/XposedSmsCode" target="_blank" rel="noopener noreferrer">https://github.com/magisk317/XposedSmsCode</a></div>
                </div>

                <div class="card">
                  <div class="tabs">
                    <button class="tab-btn active" data-tab="overview">概览</button>
                    <button class="tab-btn" data-tab="apps">应用</button>
                    <button class="tab-btn" data-tab="records">记录</button>
                    <button class="tab-btn" data-tab="advanced">高级</button>
                    <button class="tab-btn" data-tab="settings">设置</button>
                  </div>
                </div>

                <div id="panel-overview" class="tab-panel active">
                  <div class="card">
                    <div class="toolbar">
                      <h2 style="margin:0">概览</h2>
                      <button id="reloadOverview" class="btn">刷新</button>
                    </div>
                    <div class="stats-grid">
                      <div class="stat"><div id="statAppCount" class="n">0</div><div class="l">应用配置总数</div></div>
                      <div class="stat"><div id="statBlockedCount" class="n">0</div><div class="l">自动输入拦截开启</div></div>
                      <div class="stat"><div id="statForwardingCount" class="n">0</div><div class="l">应用通知转发开启</div></div>
                      <div class="stat"><div id="statRecordCount" class="n">0</div><div class="l">近期记录条数</div></div>
                    </div>
                  </div>
                </div>

                <div id="panel-apps" class="tab-panel">
                  <div class="card">
                    <div class="toolbar">
                      <h2 style="margin:0">应用控制</h2>
                      <button id="reloadApps" class="btn">刷新</button>
                    </div>
                    <table>
                      <thead>
                        <tr>
                          <th style="width:28%">应用</th>
                          <th style="width:12%">自动输入拦截</th>
                          <th style="width:12%">应用通知转发</th>
                          <th style="width:48%">应用通知模板（空=跟随全局）</th>
                        </tr>
                      </thead>
                      <tbody id="appsBody"></tbody>
                    </table>
                  </div>
                </div>

                <div id="panel-records" class="tab-panel">
                  <div class="card">
                    <div class="toolbar">
                      <h2 style="margin:0">记录</h2>
                      <button id="reloadLogs" class="btn">刷新</button>
                    </div>
                    <div class="record-desktop">
                      <div class="record-col">
                        <div id="recordDesktopCodeTitle" class="record-col-title">验证码短信（0）</div>
                        <div id="recordDesktopCodeList" class="record-list"></div>
                      </div>
                      <div class="record-col">
                        <div id="recordDesktopPlainTitle" class="record-col-title">普通短信（0）</div>
                        <div id="recordDesktopPlainList" class="record-list"></div>
                      </div>
                      <div class="record-col">
                        <div id="recordDesktopAppTitle" class="record-col-title">应用通知（0）</div>
                        <div id="recordDesktopAppList" class="record-list"></div>
                      </div>
                    </div>
                    <div class="record-mobile">
                      <div class="record-tabs">
                        <button id="recordTabCode" class="record-tab active">验证码短信（0）</button>
                        <button id="recordTabPlain" class="record-tab">普通短信（0）</button>
                        <button id="recordTabApp" class="record-tab">应用通知（0）</button>
                      </div>
                      <div id="recordList" class="record-list"></div>
                    </div>
                  </div>
                </div>

                <div id="panel-advanced" class="tab-panel">
                  <div class="card">
                    <div class="toolbar">
                      <h2 style="margin:0">高级</h2>
                      <button id="reloadAdvanced" class="btn">刷新</button>
                    </div>
                    <div class="switch-grid">
                      <label class="switch-row">
                        <span class="t">短信黑名单拦截</span>
                        <input id="advEnableSmsBlacklist" type="checkbox" />
                      </label>
                      <label class="switch-row">
                        <span class="t">WebUI 局域网访问</span>
                        <input id="advWebUiLanAccess" type="checkbox" />
                      </label>
                    </div>
                    <div style="height:10px"></div>
                    <div class="kpi">
                      <div class="item"><div id="advSenderTotal" class="n">0</div><div class="l">通道总数</div></div>
                      <div class="item"><div id="advSenderEnabled" class="n">0</div><div class="l">启用通道</div></div>
                      <div class="item"><div id="advSenderAppNotifyEnabled" class="n">0</div><div class="l">启用应用通知转发</div></div>
                    </div>
                  </div>
                  <div class="card">
                    <div class="toolbar">
                      <h2 style="margin:0">短信拦截配置</h2>
                      <button id="saveIntercept" class="btn">保存</button>
                    </div>
                    <div class="switch-grid">
                      <label class="switch-row"><span class="t">黑名单命中后删除短信</span><input id="intActionDelete" type="checkbox" /></label>
                      <label class="switch-row"><span class="t">黑名单命中后阻断广播</span><input id="intActionBlock" type="checkbox" /></label>
                    </div>
                    <div style="height:10px"></div>
                    <div class="switch-grid">
                      <div>
                        <div class="muted">黑名单号码（逗号/分号/换行）</div>
                        <textarea id="intNumbers" style="width:100%;min-height:70px;border:1px solid #d1d5db;border-radius:8px;padding:8px;"></textarea>
                      </div>
                      <div>
                        <div class="muted">黑名单号段（逗号/分号/换行）</div>
                        <textarea id="intPrefixes" style="width:100%;min-height:70px;border:1px solid #d1d5db;border-radius:8px;padding:8px;"></textarea>
                      </div>
                      <div>
                        <div class="muted">黑名单正则（逗号/分号/换行）</div>
                        <textarea id="intRegex" style="width:100%;min-height:70px;border:1px solid #d1d5db;border-radius:8px;padding:8px;"></textarea>
                      </div>
                      <div>
                        <div class="muted">黑名单内容（逗号/分号/换行）</div>
                        <textarea id="intContent" style="width:100%;min-height:70px;border:1px solid #d1d5db;border-radius:8px;padding:8px;"></textarea>
                      </div>
                    </div>
                  </div>
                  <div class="card">
                    <div class="toolbar">
                      <h2 style="margin:0">转发通道</h2>
                      <button id="newSender" class="btn">新增通道</button>
                      <button id="reloadSenders" class="btn">刷新</button>
                    </div>
                    <div id="senderEditor" class="sender-editor" style="display:none;">
                      <div class="grid">
                        <div>
                          <div class="muted">名称</div>
                          <input id="senderName" type="text" />
                        </div>
                        <div>
                          <div class="muted">类型</div>
                          <select id="senderType"></select>
                        </div>
                        <div>
                          <div class="muted">启用</div>
                          <input id="senderStatus" type="checkbox" />
                        </div>
                        <div>
                          <div class="muted">转发验证码短信</div>
                          <input id="senderReceiveCode" type="checkbox" />
                        </div>
                        <div>
                          <div class="muted">转发非验证码短信</div>
                          <input id="senderReceiveNonCode" type="checkbox" />
                        </div>
                        <div>
                          <div class="muted">转发应用通知</div>
                          <input id="senderReceiveAppNotify" type="checkbox" />
                        </div>
                        <div>
                          <div class="muted">ID</div>
                          <input id="senderId" type="text" readonly />
                        </div>
                      </div>
                      <div style="height:8px"></div>
                      <div class="muted">json_setting</div>
                      <textarea id="senderJsonSetting" style="width:100%;min-height:90px;"></textarea>
                      <div style="height:8px"></div>
                      <div class="row-actions">
                        <button id="saveSender" class="btn">保存通道</button>
                        <button id="cancelSender" class="mini-btn">取消</button>
                      </div>
                    </div>
                    <table>
                      <thead>
                        <tr>
                          <th style="width:24%">名称</th>
                          <th style="width:16%">类型</th>
                          <th style="width:10%">启用</th>
                          <th style="width:13%">转发验证码</th>
                          <th style="width:13%">转发非验证码</th>
                          <th style="width:14%">转发应用通知</th>
                          <th style="width:10%">操作</th>
                        </tr>
                      </thead>
                      <tbody id="senderBody"></tbody>
                    </table>
                  </div>
                </div>

                <div id="panel-settings" class="tab-panel">
                  <div class="card">
                    <div class="toolbar">
                      <h2 style="margin:0">设置</h2>
                      <button id="reloadSettings" class="btn">刷新</button>
                    </div>
                    <div class="settings-groups">
                      <div class="settings-group">
                        <h3 class="settings-group-title">通用设置</h3>
                        <div class="switch-grid">
                          <label class="switch-row"><span class="t">主开关</span><input id="setEnable" type="checkbox" /></label>
                        </div>
                      </div>
                      <div class="settings-group">
                        <h3 class="settings-group-title">验证码设置</h3>
                        <div class="switch-grid">
                          <label class="switch-row"><span class="t">复制到剪贴板</span><input id="setCopyToClipboard" type="checkbox" /></label>
                          <label class="switch-row"><span class="t">过滤重复短信</span><input id="setDeduplicateSms" type="checkbox" /></label>
                          <label class="switch-row"><span class="t">保留验证码记录</span><input id="setEnableCodeRecords" type="checkbox" /></label>
                        </div>
                      </div>
                      <div class="settings-group">
                        <h3 class="settings-group-title">自动输入</h3>
                        <div class="switch-grid">
                          <label class="switch-row"><span class="t">自动输入验证码</span><input id="setEnableAutoInputCode" type="checkbox" /></label>
                          <label class="switch-row"><span class="t">自动确认/回车</span><input id="setEnableAutoEnterCode" type="checkbox" /></label>
                        </div>
                      </div>
                      <div class="settings-group">
                        <h3 class="settings-group-title">通知设置</h3>
                        <div class="switch-grid">
                          <label class="switch-row"><span class="t">显示 Toast 提示</span><input id="setShowToast" type="checkbox" /></label>
                          <label class="switch-row"><span class="t">显示状态栏通知</span><input id="setShowCodeNotification" type="checkbox" /></label>
                        </div>
                      </div>
                      <div class="settings-group">
                        <h3 class="settings-group-title">实验性功能</h3>
                        <div class="switch-grid">
                          <label class="switch-row"><span class="t">拦截验证码短信</span><input id="setBlockSms" type="checkbox" /></label>
                          <label class="switch-row"><span class="t">强停后自恢复</span><input id="setForceStopRecovery" type="checkbox" /></label>
                        </div>
                      </div>
                      <div class="settings-group">
                        <h3 class="settings-group-title">其他</h3>
                        <div class="switch-grid">
                          <label class="switch-row"><span class="t">详细日志</span><input id="setVerboseLogMode" type="checkbox" /></label>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <script>
                const tabButtons = Array.from(document.querySelectorAll(".tab-btn"));
                const panels = {
                  overview: document.getElementById("panel-overview"),
                  apps: document.getElementById("panel-apps"),
                  records: document.getElementById("panel-records"),
                  advanced: document.getElementById("panel-advanced"),
                  settings: document.getElementById("panel-settings")
                };

                const statAppCount = document.getElementById("statAppCount");
                const statBlockedCount = document.getElementById("statBlockedCount");
                const statForwardingCount = document.getElementById("statForwardingCount");
                const statRecordCount = document.getElementById("statRecordCount");

                const reloadOverview = document.getElementById("reloadOverview");
                const reloadApps = document.getElementById("reloadApps");
                const reloadLogs = document.getElementById("reloadLogs");
                const reloadAdvanced = document.getElementById("reloadAdvanced");
                const reloadSettings = document.getElementById("reloadSettings");
                const reloadSenders = document.getElementById("reloadSenders");
                const saveIntercept = document.getElementById("saveIntercept");
                const appsBody = document.getElementById("appsBody");
                const senderBody = document.getElementById("senderBody");
                const recordDesktopCodeTitle = document.getElementById("recordDesktopCodeTitle");
                const recordDesktopPlainTitle = document.getElementById("recordDesktopPlainTitle");
                const recordDesktopAppTitle = document.getElementById("recordDesktopAppTitle");
                const recordDesktopCodeList = document.getElementById("recordDesktopCodeList");
                const recordDesktopPlainList = document.getElementById("recordDesktopPlainList");
                const recordDesktopAppList = document.getElementById("recordDesktopAppList");
                const recordTabCode = document.getElementById("recordTabCode");
                const recordTabPlain = document.getElementById("recordTabPlain");
                const recordTabApp = document.getElementById("recordTabApp");
                const recordList = document.getElementById("recordList");

                const advEnableSmsBlacklist = document.getElementById("advEnableSmsBlacklist");
                const advWebUiLanAccess = document.getElementById("advWebUiLanAccess");
                const advSenderTotal = document.getElementById("advSenderTotal");
                const advSenderEnabled = document.getElementById("advSenderEnabled");
                const advSenderAppNotifyEnabled = document.getElementById("advSenderAppNotifyEnabled");
                const intActionDelete = document.getElementById("intActionDelete");
                const intActionBlock = document.getElementById("intActionBlock");
                const intNumbers = document.getElementById("intNumbers");
                const intPrefixes = document.getElementById("intPrefixes");
                const intRegex = document.getElementById("intRegex");
                const intContent = document.getElementById("intContent");

                const setEnable = document.getElementById("setEnable");
                const setCopyToClipboard = document.getElementById("setCopyToClipboard");
                const setShowToast = document.getElementById("setShowToast");
                const setShowCodeNotification = document.getElementById("setShowCodeNotification");
                const setEnableAutoInputCode = document.getElementById("setEnableAutoInputCode");
                const setEnableAutoEnterCode = document.getElementById("setEnableAutoEnterCode");
                const setDeduplicateSms = document.getElementById("setDeduplicateSms");
                const setBlockSms = document.getElementById("setBlockSms");
                const setForceStopRecovery = document.getElementById("setForceStopRecovery");
                const setEnableCodeRecords = document.getElementById("setEnableCodeRecords");
                const setVerboseLogMode = document.getElementById("setVerboseLogMode");
                const newSender = document.getElementById("newSender");
                const senderEditor = document.getElementById("senderEditor");
                const senderId = document.getElementById("senderId");
                const senderName = document.getElementById("senderName");
                const senderType = document.getElementById("senderType");
                const senderStatus = document.getElementById("senderStatus");
                const senderReceiveCode = document.getElementById("senderReceiveCode");
                const senderReceiveNonCode = document.getElementById("senderReceiveNonCode");
                const senderReceiveAppNotify = document.getElementById("senderReceiveAppNotify");
                const senderJsonSetting = document.getElementById("senderJsonSetting");
                const saveSender = document.getElementById("saveSender");
                const cancelSender = document.getElementById("cancelSender");
                const senderTypes = [
                  { value: 0, label: "钉钉群机器人" },
                  { value: 1, label: "邮件" },
                  { value: 2, label: "Bark" },
                  { value: 3, label: "Webhook" },
                  { value: 4, label: "企业微信机器人" },
                  { value: 5, label: "企业微信应用" },
                  { value: 6, label: "Server酱" },
                  { value: 7, label: "Telegram" },
                  { value: 8, label: "短信" },
                  { value: 9, label: "飞书" },
                  { value: 10, label: "PushPlus" },
                  { value: 11, label: "Gotify" },
                  { value: 12, label: "钉钉内部机器人" },
                  { value: 13, label: "飞书应用" },
                  { value: 14, label: "URL Scheme" },
                  { value: 15, label: "Socket" }
                ];

                let latestApps = [];
                let latestRecords = [];
                let latestAdvanced = null;
                let latestSettings = null;
                let latestIntercept = null;
                let latestSenders = [];
                let activeRecordTab = "code";

                function setActiveTab(tabName) {
                  tabButtons.forEach((btn) => {
                    const active = btn.dataset.tab === tabName;
                    btn.classList.toggle("active", active);
                  });
                  Object.keys(panels).forEach((key) => {
                    panels[key].classList.toggle("active", key === tabName);
                  });
                }

                tabButtons.forEach((btn) => {
                  btn.addEventListener("click", () => setActiveTab(btn.dataset.tab));
                });

                async function fetchJson(url, options) {
                  const resp = await fetch(url, options);
                  if (!resp.ok) {
                    throw new Error("HTTP " + resp.status + " " + (await resp.text()));
                  }
                  return await resp.json();
                }

                async function updateApp(pkg, payload) {
                  await fetchJson("/api/apps/" + encodeURIComponent(pkg), {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                  });
                }

                async function updateAdvanced(payload) {
                  await fetchJson("/api/advanced", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                  });
                }

                async function updateSettings(payload) {
                  await fetchJson("/api/settings", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                  });
                }

                async function updateIntercept(payload) {
                  await fetchJson("/api/intercept", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                  });
                }

                async function updateSender(senderId, payload) {
                  await fetchJson("/api/senders/" + senderId, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                  });
                }

                async function createSender(payload) {
                  return await fetchJson("/api/senders", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                  });
                }

                async function deleteSender(senderId) {
                  await fetchJson("/api/senders/" + senderId + "/delete", {
                    method: "POST"
                  });
                }

                async function deleteRecord(recordId) {
                  await fetchJson("/api/records/" + recordId + "/delete", {
                    method: "POST"
                  });
                }

                function renderOverview() {
                  statAppCount.textContent = String(latestApps.length);
                  statBlockedCount.textContent = String(latestApps.filter((it) => !!it.blocked).length);
                  statForwardingCount.textContent = String(latestApps.filter((it) => !!it.forwarding).length);
                  statRecordCount.textContent = String(latestRecords.length);
                }

                function renderApps(items) {
                  appsBody.innerHTML = "";
                  for (const app of items) {
                    const tr = document.createElement("tr");

                    const appCol = document.createElement("td");
                    appCol.textContent = app.label + " (" + app.packageName + ")";
                    tr.appendChild(appCol);

                    const blockCol = document.createElement("td");
                    const block = document.createElement("input");
                    block.type = "checkbox";
                    block.checked = !!app.blocked;
                    block.onchange = async () => {
                      const preserveScroll = { x: window.scrollX, y: window.scrollY };
                      block.blur();
                      try {
                        await updateApp(app.packageName, { blocked: block.checked });
                        await loadApps(preserveScroll);
                      } catch (e) {
                        alert("更新失败: " + e.message);
                        block.checked = !block.checked;
                      }
                    };
                    blockCol.appendChild(block);
                    tr.appendChild(blockCol);

                    const forwardCol = document.createElement("td");
                    const forward = document.createElement("input");
                    forward.type = "checkbox";
                    forward.checked = !!app.forwarding;
                    forward.onchange = async () => {
                      const preserveScroll = { x: window.scrollX, y: window.scrollY };
                      forward.blur();
                      try {
                        await updateApp(app.packageName, { forwarding: forward.checked });
                        await loadApps(preserveScroll);
                      } catch (e) {
                        alert("更新失败: " + e.message);
                        forward.checked = !forward.checked;
                      }
                    };
                    forwardCol.appendChild(forward);
                    tr.appendChild(forwardCol);

                    const templateCol = document.createElement("td");
                    const template = document.createElement("input");
                    template.type = "text";
                    template.value = app.notifyTemplate || "";
                    template.placeholder = "例如: [{sender}] {body}";
                    template.onblur = async () => {
                      try {
                        await updateApp(app.packageName, { notifyTemplate: template.value });
                      } catch (e) {
                        alert("模板保存失败: " + e.message);
                      }
                    };
                    templateCol.appendChild(template);
                    tr.appendChild(templateCol);

                    appsBody.appendChild(tr);
                  }
                }

                function splitRecordGroups(items) {
                  const code = [];
                  const plain = [];
                  const app = [];
                  for (const item of items || []) {
                    if (item.msgType === 1) {
                      app.push(item);
                    } else if (item.msgType === 0 && !!item.smsCode) {
                      code.push(item);
                    } else {
                      plain.push(item);
                    }
                  }
                  return { code: code, plain: plain, app: app };
                }

                function normalizeForwardMessage(message) {
                  return String(message || "").replace(/\s*\|\s*/g, "\n").trim();
                }

                function parseForwardCounts(message) {
                  const lines = normalizeForwardMessage(message)
                    .split(/\n+/)
                    .map((line) => line.trim())
                    .filter(Boolean);
                  let success = 0;
                  let failed = 0;
                  for (const line of lines) {
                    if (line.includes("转发成功")) success += 1;
                    if (line.includes("转发失败")) failed += 1;
                  }
                  return { success: success, failed: failed };
                }

                function countForwardTargets(target) {
                  return String(target || "")
                    .split(/[|,]/)
                    .map((part) => part.trim())
                    .filter(Boolean)
                    .length;
                }

                function forwardResultText(item) {
                  const status = Number(item.forwardStatus || 0);
                  if (status === 4) return "未转发";
                  const counts = parseForwardCounts(item.forwardMessage);
                  const targetCount = countForwardTargets(item.forwardTarget);
                  const successCount = counts.success > 0 ? counts.success : (status === 1 ? Math.max(targetCount, 1) : 0);
                  const failedCount = counts.failed > 0 ? counts.failed : (status === 2 ? Math.max(targetCount, 1) : 0);
                  if (status === 3) {
                    return Math.max(successCount, 1) + "通道转发成功" + Math.max(failedCount, 1) + "通道转发失败";
                  }
                  if (status === 1) {
                    return Math.max(successCount, 1) + "通道转发成功";
                  }
                  if (status === 2) {
                    return Math.max(failedCount, 1) + "通道转发失败";
                  }
                  return "未转发";
                }

                function renderRecordCard(item) {
                  const card = document.createElement("div");
                  card.className = "record-card";

                  const top = document.createElement("div");
                  top.className = "top";
                  const sender = document.createElement("div");
                  sender.className = "sender";
                  sender.textContent = item.sender || item.packageName || "-";
                  const topRight = document.createElement("div");
                  topRight.className = "top-right";
                  const time = document.createElement("div");
                  time.className = "time";
                  time.textContent = new Date(item.date).toLocaleString();
                  topRight.appendChild(time);

                  if (Number(item.id) > 0) {
                    const deleteBtn = document.createElement("button");
                    deleteBtn.className = "mini-btn danger record-delete-btn";
                    deleteBtn.textContent = "删除";
                    deleteBtn.onclick = async () => {
                      if (!confirm("确认删除该记录吗？")) return;
                      deleteBtn.disabled = true;
                      try {
                        await deleteRecord(item.id);
                        await loadLogs();
                      } catch (e) {
                        alert("删除记录失败: " + e.message);
                        deleteBtn.disabled = false;
                      }
                    };
                    topRight.appendChild(deleteBtn);
                  }
                  top.appendChild(sender);
                  top.appendChild(topRight);

                  const body = document.createElement("div");
                  body.className = "body";
                  body.textContent = item.body || "(空内容)";

                  const meta = document.createElement("div");
                  meta.className = "meta";
                  const kind = document.createElement("span");
                  kind.className = "badge";
                  kind.textContent = item.msgType === 1 ? "应用通知" : (!!item.smsCode ? "验证码短信" : "普通短信");
                  meta.appendChild(kind);

                  if (item.smsCode) {
                    const smsCode = document.createElement("span");
                    smsCode.textContent = "验证码: " + item.smsCode;
                    meta.appendChild(smsCode);
                  }

                  const forward = document.createElement("span");
                  forward.textContent = forwardResultText(item);
                  meta.appendChild(forward);

                  const forwardDetail = normalizeForwardMessage(item.forwardMessage);
                  if (forwardDetail) {
                    const message = document.createElement("span");
                    message.className = "forward-detail";
                    message.textContent = "结果:\n" + forwardDetail;
                    meta.appendChild(message);
                  }

                  card.appendChild(top);
                  card.appendChild(body);
                  card.appendChild(meta);
                  return card;
                }

                function renderRecordList(container, list, emptyText) {
                  container.innerHTML = "";
                  if (!list || list.length === 0) {
                    const empty = document.createElement("div");
                    empty.className = "muted";
                    empty.textContent = emptyText;
                    container.appendChild(empty);
                    return;
                  }
                  for (const item of list) {
                    container.appendChild(renderRecordCard(item));
                  }
                }

                function renderLogs(items) {
                  const grouped = splitRecordGroups(items);
                  recordDesktopCodeTitle.textContent = "验证码短信（" + grouped.code.length + "）";
                  recordDesktopPlainTitle.textContent = "普通短信（" + grouped.plain.length + "）";
                  recordDesktopAppTitle.textContent = "应用通知（" + grouped.app.length + "）";
                  renderRecordList(recordDesktopCodeList, grouped.code, "暂无验证码短信");
                  renderRecordList(recordDesktopPlainList, grouped.plain, "暂无普通短信");
                  renderRecordList(recordDesktopAppList, grouped.app, "暂无应用通知");

                  recordTabCode.textContent = "验证码短信（" + grouped.code.length + "）";
                  recordTabPlain.textContent = "普通短信（" + grouped.plain.length + "）";
                  recordTabApp.textContent = "应用通知（" + grouped.app.length + "）";

                  recordTabCode.classList.toggle("active", activeRecordTab === "code");
                  recordTabPlain.classList.toggle("active", activeRecordTab === "plain");
                  recordTabApp.classList.toggle("active", activeRecordTab === "app");

                  let selected = grouped.code;
                  if (activeRecordTab === "plain") selected = grouped.plain;
                  if (activeRecordTab === "app") selected = grouped.app;
                  renderRecordList(recordList, selected, "暂无记录");
                }

                function renderAdvanced(state) {
                  if (!state) return;
                  advEnableSmsBlacklist.checked = !!state.enableSmsBlacklist;
                  advWebUiLanAccess.checked = !!state.webUiLanAccess;
                  advSenderTotal.textContent = String(state.senderTotal || 0);
                  advSenderEnabled.textContent = String(state.senderEnabled || 0);
                  advSenderAppNotifyEnabled.textContent = String(state.senderAppNotifyEnabled || 0);
                }

                function renderSettings(state) {
                  if (!state) return;
                  setEnable.checked = !!state.enable;
                  setCopyToClipboard.checked = !!state.copyToClipboard;
                  setShowToast.checked = !!state.showToast;
                  setShowCodeNotification.checked = !!state.showCodeNotification;
                  setEnableAutoInputCode.checked = !!state.enableAutoInputCode;
                  setEnableAutoEnterCode.checked = !!state.enableAutoEnterCode;
                  setDeduplicateSms.checked = !!state.deduplicateSms;
                  setBlockSms.checked = !!state.blockSms;
                  setForceStopRecovery.checked = !!state.forceStopRecovery;
                  setEnableCodeRecords.checked = !!state.enableCodeRecords;
                  setVerboseLogMode.checked = !!state.verboseLogMode;
                }

                function renderIntercept(state) {
                  if (!state) return;
                  intActionDelete.checked = !!state.smsBlacklistActionDelete;
                  intActionBlock.checked = !!state.smsBlacklistActionBlock;
                  intNumbers.value = state.smsBlacklistNumbers || "";
                  intPrefixes.value = state.smsBlacklistPrefixes || "";
                  intRegex.value = state.smsBlacklistRegex || "";
                  intContent.value = state.smsBlacklistContent || "";
                }

                function createSenderSwitch(checked, onChange) {
                  const input = document.createElement("input");
                  input.type = "checkbox";
                  input.checked = !!checked;
                  input.onchange = () => onChange(input.checked);
                  return input;
                }

                function ensureSenderTypeOptions() {
                  senderType.innerHTML = "";
                  for (const type of senderTypes) {
                    const option = document.createElement("option");
                    option.value = String(type.value);
                    option.textContent = type.label + " (" + type.value + ")";
                    senderType.appendChild(option);
                  }
                }

                function openSenderEditorForCreate() {
                  senderId.value = "";
                  senderName.value = "";
                  senderType.value = String(senderTypes[0].value);
                  senderStatus.checked = true;
                  senderReceiveCode.checked = true;
                  senderReceiveNonCode.checked = true;
                  senderReceiveAppNotify.checked = true;
                  senderJsonSetting.value = "";
                  saveSender.textContent = "创建通道";
                  senderEditor.style.display = "block";
                }

                function openSenderEditorForEdit(sender) {
                  senderId.value = String(sender.id);
                  senderName.value = sender.name || "";
                  senderType.value = String(sender.type);
                  senderStatus.checked = !!sender.status;
                  senderReceiveCode.checked = !!sender.receiveCode;
                  senderReceiveNonCode.checked = !!sender.receiveNonCode;
                  senderReceiveAppNotify.checked = !!sender.receiveAppNotify;
                  senderJsonSetting.value = sender.jsonSetting || "";
                  saveSender.textContent = "保存修改";
                  senderEditor.style.display = "block";
                }

                function closeSenderEditor() {
                  senderEditor.style.display = "none";
                }

                function renderSenders(items) {
                  senderBody.innerHTML = "";
                  for (const sender of items) {
                    const tr = document.createElement("tr");

                    const c1 = document.createElement("td");
                    c1.textContent = sender.name || ("#" + sender.id);
                    tr.appendChild(c1);

                    const c2 = document.createElement("td");
                    c2.textContent = sender.typeLabel + " (" + sender.type + ")";
                    tr.appendChild(c2);

                    const c3 = document.createElement("td");
                    c3.appendChild(createSenderSwitch(sender.status, async (checked) => {
                      try {
                        await updateSender(sender.id, { status: checked });
                        await Promise.all([loadSenders(), loadAdvanced()]);
                      } catch (e) {
                        alert("通道状态更新失败: " + e.message);
                      }
                    }));
                    tr.appendChild(c3);

                    const c4 = document.createElement("td");
                    c4.appendChild(createSenderSwitch(sender.receiveCode, async (checked) => {
                      try {
                        await updateSender(sender.id, { receiveCode: checked });
                        await loadSenders();
                      } catch (e) {
                        alert("通道配置更新失败: " + e.message);
                      }
                    }));
                    tr.appendChild(c4);

                    const c5 = document.createElement("td");
                    c5.appendChild(createSenderSwitch(sender.receiveNonCode, async (checked) => {
                      try {
                        await updateSender(sender.id, { receiveNonCode: checked });
                        await loadSenders();
                      } catch (e) {
                        alert("通道配置更新失败: " + e.message);
                      }
                    }));
                    tr.appendChild(c5);

                    const c6 = document.createElement("td");
                    c6.appendChild(createSenderSwitch(sender.receiveAppNotify, async (checked) => {
                      try {
                        await updateSender(sender.id, { receiveAppNotify: checked });
                        await Promise.all([loadSenders(), loadAdvanced()]);
                      } catch (e) {
                        alert("通道配置更新失败: " + e.message);
                      }
                    }));
                    tr.appendChild(c6);

                    const c7 = document.createElement("td");
                    const actions = document.createElement("div");
                    actions.className = "row-actions";
                    const editBtn = document.createElement("button");
                    editBtn.className = "mini-btn";
                    editBtn.textContent = "编辑";
                    editBtn.onclick = () => openSenderEditorForEdit(sender);
                    const deleteBtn = document.createElement("button");
                    deleteBtn.className = "mini-btn danger";
                    deleteBtn.textContent = "删除";
                    deleteBtn.onclick = async () => {
                      if (!confirm("确认删除通道「" + (sender.name || ("#" + sender.id)) + "」吗？")) return;
                      try {
                        await deleteSender(sender.id);
                        closeSenderEditor();
                        await Promise.all([loadSenders(), loadAdvanced()]);
                      } catch (e) {
                        alert("删除通道失败: " + e.message);
                      }
                    };
                    actions.appendChild(editBtn);
                    actions.appendChild(deleteBtn);
                    c7.appendChild(actions);
                    tr.appendChild(c7);

                    senderBody.appendChild(tr);
                  }
                  if (!items || items.length === 0) {
                    const tr = document.createElement("tr");
                    const td = document.createElement("td");
                    td.colSpan = 6;
                    td.className = "muted";
                    td.textContent = "暂无转发通道";
                    tr.appendChild(td);
                    senderBody.appendChild(tr);
                  }
                }

                function restoreWindowScroll(pos) {
                  if (!pos) return;
                  requestAnimationFrame(() => {
                    requestAnimationFrame(() => {
                      window.scrollTo(pos.x, pos.y);
                    });
                  });
                }

                async function loadApps(preserveScroll) {
                  reloadApps.disabled = true;
                  try {
                    latestApps = await fetchJson("/api/apps");
                    renderApps(latestApps);
                    renderOverview();
                  } finally {
                    reloadApps.disabled = false;
                    restoreWindowScroll(preserveScroll);
                  }
                }

                async function loadLogs() {
                  reloadLogs.disabled = true;
                  try {
                    latestRecords = await fetchJson("/api/records?limit=80");
                    renderLogs(latestRecords);
                    renderOverview();
                  } finally {
                    reloadLogs.disabled = false;
                  }
                }

                async function loadAdvanced() {
                  reloadAdvanced.disabled = true;
                  try {
                    latestAdvanced = await fetchJson("/api/advanced");
                    renderAdvanced(latestAdvanced);
                  } finally {
                    reloadAdvanced.disabled = false;
                  }
                }

                async function loadSettings() {
                  reloadSettings.disabled = true;
                  try {
                    latestSettings = await fetchJson("/api/settings");
                    renderSettings(latestSettings);
                  } finally {
                    reloadSettings.disabled = false;
                  }
                }

                async function loadIntercept() {
                  saveIntercept.disabled = true;
                  try {
                    latestIntercept = await fetchJson("/api/intercept");
                    renderIntercept(latestIntercept);
                  } finally {
                    saveIntercept.disabled = false;
                  }
                }

                async function loadSenders() {
                  reloadSenders.disabled = true;
                  try {
                    latestSenders = await fetchJson("/api/senders");
                    renderSenders(latestSenders);
                  } finally {
                    reloadSenders.disabled = false;
                  }
                }

                async function bindToggle(inputEl, updater) {
                  inputEl.addEventListener("change", async () => {
                    const expected = inputEl.checked;
                    inputEl.disabled = true;
                    try {
                      await updater(expected);
                    } catch (e) {
                      inputEl.checked = !expected;
                      alert("更新失败: " + e.message);
                    } finally {
                      inputEl.disabled = false;
                    }
                  });
                }

                async function loadAll() {
                  await Promise.all([
                    loadApps(),
                    loadLogs(),
                    loadAdvanced(),
                    loadSettings(),
                    loadIntercept(),
                    loadSenders()
                  ]);
                }

                bindToggle(advEnableSmsBlacklist, async (v) => {
                  await updateAdvanced({ enableSmsBlacklist: v });
                  await loadAdvanced();
                });
                bindToggle(advWebUiLanAccess, async (v) => {
                  await updateAdvanced({ webUiLanAccess: v });
                  await loadAdvanced();
                });
                bindToggle(setEnable, async (v) => {
                  await updateSettings({ enable: v });
                  await loadSettings();
                });
                bindToggle(setCopyToClipboard, async (v) => {
                  await updateSettings({ copyToClipboard: v });
                  await loadSettings();
                });
                bindToggle(setShowToast, async (v) => {
                  await updateSettings({ showToast: v });
                  await loadSettings();
                });
                bindToggle(setShowCodeNotification, async (v) => {
                  await updateSettings({ showCodeNotification: v });
                  await loadSettings();
                });
                bindToggle(setEnableAutoInputCode, async (v) => {
                  await updateSettings({ enableAutoInputCode: v });
                  await loadSettings();
                });
                bindToggle(setEnableAutoEnterCode, async (v) => {
                  await updateSettings({ enableAutoEnterCode: v });
                  await loadSettings();
                });
                bindToggle(setDeduplicateSms, async (v) => {
                  await updateSettings({ deduplicateSms: v });
                  await loadSettings();
                });
                bindToggle(setBlockSms, async (v) => {
                  await updateSettings({ blockSms: v });
                  await loadSettings();
                });
                bindToggle(setForceStopRecovery, async (v) => {
                  await updateSettings({ forceStopRecovery: v });
                  await loadSettings();
                });
                bindToggle(setEnableCodeRecords, async (v) => {
                  await updateSettings({ enableCodeRecords: v });
                  await loadSettings();
                });
                bindToggle(setVerboseLogMode, async (v) => {
                  await updateSettings({ verboseLogMode: v });
                  await loadSettings();
                });

                saveIntercept.onclick = async () => {
                  saveIntercept.disabled = true;
                  try {
                    await updateIntercept({
                      smsBlacklistNumbers: intNumbers.value || "",
                      smsBlacklistPrefixes: intPrefixes.value || "",
                      smsBlacklistRegex: intRegex.value || "",
                      smsBlacklistContent: intContent.value || "",
                      smsBlacklistActionDelete: intActionDelete.checked,
                      smsBlacklistActionBlock: intActionBlock.checked
                    });
                    await Promise.all([loadIntercept(), loadAdvanced()]);
                  } catch (e) {
                    alert("拦截配置保存失败: " + e.message);
                  } finally {
                    saveIntercept.disabled = false;
                  }
                };

                recordTabCode.onclick = () => {
                  activeRecordTab = "code";
                  renderLogs(latestRecords);
                };
                recordTabPlain.onclick = () => {
                  activeRecordTab = "plain";
                  renderLogs(latestRecords);
                };
                recordTabApp.onclick = () => {
                  activeRecordTab = "app";
                  renderLogs(latestRecords);
                };

                newSender.onclick = () => {
                  openSenderEditorForCreate();
                };

                cancelSender.onclick = () => {
                  closeSenderEditor();
                };

                saveSender.onclick = async () => {
                  const name = (senderName.value || "").trim();
                  if (!name) {
                    alert("通道名称不能为空");
                    return;
                  }
                  const payload = {
                    name: name,
                    type: Number(senderType.value),
                    jsonSetting: senderJsonSetting.value || "",
                    status: senderStatus.checked,
                    receiveCode: senderReceiveCode.checked,
                    receiveNonCode: senderReceiveNonCode.checked,
                    receiveAppNotify: senderReceiveAppNotify.checked
                  };
                  saveSender.disabled = true;
                  try {
                    if (senderId.value) {
                      await updateSender(Number(senderId.value), payload);
                    } else {
                      await createSender(payload);
                    }
                    closeSenderEditor();
                    await Promise.all([loadSenders(), loadAdvanced()]);
                  } catch (e) {
                    alert("通道保存失败: " + e.message);
                  } finally {
                    saveSender.disabled = false;
                  }
                };

                reloadOverview.onclick = loadAll;
                reloadApps.onclick = loadApps;
                reloadLogs.onclick = loadLogs;
                reloadAdvanced.onclick = async () => { await Promise.all([loadAdvanced(), loadIntercept(), loadSenders()]); };
                reloadSettings.onclick = loadSettings;
                reloadSenders.onclick = loadSenders;
                ensureSenderTypeOptions();
                setActiveTab("overview");
                loadAll();
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}

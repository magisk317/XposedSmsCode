package com.github.magisk317.smscode.common.utils

import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import android.annotation.SuppressLint
import android.content.Context
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.data.db.DBProvider
import com.github.magisk317.smscode.data.db.entity.SmsCodeRule
import com.github.magisk317.smscode.feature.store.EntityStoreManager
import com.github.magisk317.smscode.feature.store.EntityType
import io.github.magisk317.smscode.rule.catalog.SmsCodeRuleMerger
import io.github.magisk317.smscode.rule.model.SmsCodeParseResult
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogRefreshResult
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogRepository
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogSnapshot
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogSourceKind
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleRemoteSource
import io.github.magisk317.smscode.runtime.common.sms.RuntimeSmsCodeAdapter
import io.github.magisk317.smscode.runtime.common.sms.SmsCodeRuleProvider
import io.github.magisk317.smscode.runtime.common.sms.SmsKeywordProvider
import io.github.magisk317.smscode.runtime.common.sms.SmsPackageLabelResolver
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SmsCodeUtils {
    private const val COLUMN_COMPANY = "company"
    private const val COLUMN_KEYWORD = "code_keyword"
    private const val COLUMN_REGEX = "code_regex"

    /**
     * TTL for the in-process user-rule cache. Each incoming SMS otherwise triggers a
     * cross-process ContentResolver.query to the module app's DBProvider; when that
     * process is frozen (NoActive) the Binder call blocks for tens of seconds and the
     * parse times out. A short cache lets the hook reuse the last rules and keeps the
     * IPC off the per-SMS hot path, while staying fresh enough to pick up edits made
     * in the settings UI a few seconds later (no live listener exists in the hook
     * process, mirroring XposedRuntimeInstaller.SANITIZER_SYNC_TTL_MS).
     */
    private const val RULE_CACHE_TTL_MS = 30_000L

    /**
     * TTL for the in-process official-rule snapshot. [loadOfficialRuleSnapshot] walks
     * the on-disk catalog (index + SHA checks + JSON parse) on every SMS merge; when
     * that path is cold or the module files dir is slow it can dominate the 10s parse
     * timeout. Caching the last good snapshot keeps the hot path off the filesystem
     * while still picking up a manual refresh within a minute.
     */
    private const val OFFICIAL_RULE_CACHE_TTL_MS = 60_000L

    private val ruleCache = TtlValueCache<List<SmsCodeRule>>(RULE_CACHE_TTL_MS)

    private class OfficialSnapshotEntry(
        val snapshot: SmsCodeRuleCatalogSnapshot,
        val at: Long,
    )

    // Suspend-safe official snapshot cache: catalog load is suspend I/O, so we cannot
    // park it inside the blocking [TtlValueCache] loader without nested runBlocking.
    private val officialRuleCache = AtomicReference<OfficialSnapshotEntry?>(null)
    private val officialRuleMutex = Mutex()

    /**
     * Clears the cached user rules so the next parse re-reads the provider. Intended
     * to be called from the module process when the user edits rules, so changes
     * take effect immediately instead of waiting out the TTL.
     */
    @JvmStatic
    fun invalidateRuleCache() {
        ruleCache.invalidate()
    }

    /**
     * Drops the cached official-rule snapshot so the next parse reloads from disk /
     * bundled assets. Call after a successful [refreshOfficialRules].
     */
    @JvmStatic
    fun invalidateOfficialRuleCache() {
        officialRuleCache.set(null)
    }

    private val adapter = RuntimeSmsCodeAdapter(
        keywordProvider = SmsKeywordProvider { context, override ->
            override ?: HookPrefsReader.getSMSCodeKeywords(context).orEmpty()
        },
        ruleProvider = SmsCodeRuleProvider { context ->
            loadMergedRuleSpecs(context)
        },
        labelResolver = SmsPackageLabelResolver { context, label ->
            resolvePackageNameByLabel(context, label)
        },
    )

    suspend fun parseSmsCodeIfExists(
        context: Context,
        content: String,
        source: SmsCodeParseSource? = null,
        keywordsRegex: String? = null,
    ): String {
        return adapter.parseSmsCodeIfExists(
            context,
            content,
            override = keywordsRegex,
            source = source,
        )
    }

    suspend fun parseSmsCodeResultIfExists(
        context: Context,
        content: String,
        source: SmsCodeParseSource? = null,
        keywordsRegex: String? = null,
    ): SmsCodeParseResult {
        return adapter.parseSmsCodeResultIfExists(
            context,
            content,
            override = keywordsRegex,
            source = source,
        )
    }

    @JvmStatic
    fun parseCompany(content: String): String = adapter.parseCompany(content)

    @JvmStatic
    fun parseCompanyCandidates(content: String): List<String> = adapter.parseCompanyCandidates(content)

    fun findPackageNameByLabel(context: Context, label: String?): String? {
        return adapter.findPackageNameByLabel(context, label)
    }

    suspend fun loadOfficialRuleSnapshot(context: Context): SmsCodeRuleCatalogSnapshot {
        val now = android.os.SystemClock.elapsedRealtime()
        val hit = officialRuleCache.get()
        if (hit != null && now - hit.at < OFFICIAL_RULE_CACHE_TTL_MS) {
            return hit.snapshot
        }
        return officialRuleMutex.withLock {
            val now2 = android.os.SystemClock.elapsedRealtime()
            val again = officialRuleCache.get()
            if (again != null && now2 - again.at < OFFICIAL_RULE_CACHE_TTL_MS) {
                return@withLock again.snapshot
            }
            val fresh = runCatching {
                catalogRepository(context).loadOfficialRules().also(::logRejectedOfficialRules)
            }.getOrNull()
            if (fresh != null) {
                officialRuleCache.set(OfficialSnapshotEntry(fresh, now2))
                fresh
            } else {
                // Keep last good snapshot across load failures (slow/missing files).
                again?.snapshot ?: SmsCodeRuleCatalogSnapshot(
                    sourceKind = SmsCodeRuleCatalogSourceKind.EMPTY,
                    index = null,
                    rules = emptyList(),
                )
            }
        }
    }

    suspend fun refreshOfficialRules(context: Context): SmsCodeRuleCatalogRefreshResult {
        return catalogRepository(context).refreshOfficialRules().also { result ->
            result.snapshot?.let { snapshot ->
                logRejectedOfficialRules(snapshot)
                // Successful refresh replaces the in-process snapshot immediately.
                officialRuleCache.set(
                    OfficialSnapshotEntry(
                        snapshot = snapshot,
                        at = android.os.SystemClock.elapsedRealtime(),
                    ),
                )
                // Hook processes keep their own official snapshot; signal them to drop it.
                DBProvider.notifyRulesCacheChanged(context)
            }
            if (!result.success) {
                XLog.w("Refresh official SmsCode rules failed: %s", result.errorMessage ?: "unknown")
            }
        }
    }

    fun observeOfficialRuleSourceUrl(context: Context): Flow<String> =
        AppPreferencesDataStore.getStringFlow(
            context,
            PrefConst.KEY_SMS_CODE_RULE_SOURCE_URL,
            "",
        )

    suspend fun saveOfficialRuleSourceUrl(context: Context, value: String): String {
        val normalized = SmsCodeRuleRemoteSource.normalizeCustomBaseUrl(value).orEmpty()
        AppPreferencesDataStore.setString(
            context,
            PrefConst.KEY_SMS_CODE_RULE_SOURCE_URL,
            normalized,
        )
        invalidateOfficialRuleCache()
        return normalized
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun resolvePackageNameByLabel(context: Context, label: String): String? {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.MATCH_ALL)
            val targetLabel = label.trim()
            for (app in apps) {
                if (pm.getApplicationLabel(app).toString().trim().equals(targetLabel, ignoreCase = true)) {
                    return app.packageName
                }
            }
            val normalizedTarget = normalizeLabelForMatching(targetLabel)
            if (normalizedTarget.isBlank()) return null
            for (app in apps) {
                val appLabel = pm.getApplicationLabel(app).toString().trim()
                val normalizedAppLabel = normalizeLabelForMatching(appLabel)
                if (normalizedAppLabel.isBlank()) continue
                if (
                    normalizedAppLabel == normalizedTarget ||
                    normalizedAppLabel.contains(normalizedTarget) ||
                    normalizedTarget.contains(normalizedAppLabel)
                ) {
                    return app.packageName
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeLabelForMatching(label: String): String {
        return label
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\s\\p{Punct}·、，。！（）【】《》「」『』：；]+"), "")
    }

    private fun loadRulesFromFile(context: Context): List<SmsCodeRule> =
        EntityStoreManager.loadEntitiesFromFile(
            context,
            EntityType.CODE_RULES,
            SmsCodeRule::class.java,
        )

    private fun logProviderEmptyFallback(fileRules: List<SmsCodeRule>) {
        if (fileRules.isEmpty()) {
            XLog.d("Load SmsCode rules by file: provider empty and no persisted user rules")
        } else {
            XLog.w(
                "Load SmsCode rules by file: provider empty but %d persisted user rule(s) found",
                fileRules.size,
            )
        }
    }

    private fun logProviderFailureFallback(fileRules: List<SmsCodeRule>, throwable: Throwable) {
        if (fileRules.isEmpty()) {
            XLog.d(
                "Load SmsCode rules by file after provider failure: no persisted user rules, err=%s",
                throwable.message ?: throwable.javaClass.simpleName,
            )
        } else {
            XLog.w(
                "Load SmsCode rules by file after provider failure: %d persisted user rule(s) found, err=%s",
                fileRules.size,
                throwable.message ?: throwable.javaClass.simpleName,
            )
        }
    }

    private fun queryAllSmsCodeRules(context: Context): List<SmsCodeRule> {
        return ruleCache.get { queryAllSmsCodeRulesUncached(context) } ?: emptyList()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun queryAllSmsCodeRulesUncached(context: Context): List<SmsCodeRule> {
        var rules: List<SmsCodeRule>
        try {
            val smsCodeRuleUri = DBProvider.smsCodeRuleContentUri(context)
            val projection = arrayOf(COLUMN_COMPANY, COLUMN_KEYWORD, COLUMN_REGEX)
            context.contentResolver.query(smsCodeRuleUri, projection, null, null, null)?.use { cursor ->
                val resultRules = mutableListOf<SmsCodeRule>()
                while (cursor.moveToNext()) {
                    resultRules.add(
                        SmsCodeRule(
                            company = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COMPANY)),
                            codeKeyword = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_KEYWORD)),
                            codeRegex = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REGEX)),
                        ),
                    )
                }
                rules = if (resultRules.isNotEmpty()) {
                    XLog.d("Load SmsCode rules succeed by content provider")
                    resultRules
                } else {
                    loadRulesFromFile(context).also(::logProviderEmptyFallback)
                }
                return rules
            }
            throw IllegalStateException("Cursor is null for URI: $smsCodeRuleUri")
        } catch (throwable: Exception) {
            rules = loadRulesFromFile(context)
            logProviderFailureFallback(rules, throwable)
        }
        return rules
    }

    private fun SmsCodeRule.toSpec(): SmsCodeRuleSpec =
        SmsCodeRuleSpec(
            company = company,
            codeKeyword = codeKeyword,
            codeRegex = codeRegex,
        )

    private suspend fun loadMergedRuleSpecs(context: Context): List<SmsCodeRuleSpec> {
        val userRules = queryAllSmsCodeRules(context).map { it.toSpec() }
        val officialSnapshot = loadOfficialRuleSnapshot(context)
        return SmsCodeRuleMerger.merge(
            userRules = userRules,
            officialRules = officialSnapshot.rules,
        )
    }

    private suspend fun catalogRepository(context: Context): SmsCodeRuleCatalogRepository {
        val appContext = context.applicationContext ?: context
        val customBaseUrl = AppPreferencesDataStore.getString(
            appContext,
            PrefConst.KEY_SMS_CODE_RULE_SOURCE_URL,
            "",
        )
        return SmsCodeRuleCatalogRepository(
            context = appContext,
            remote = SmsCodeRuleRemoteSource(customBaseUrl = customBaseUrl),
            userAgent = "XposedSmsCode/SmsCodeRules",
        )
    }

    private fun logRejectedOfficialRules(snapshot: SmsCodeRuleCatalogSnapshot) {
        if (snapshot.rejectedRules.isEmpty()) return
        XLog.w(
            "Official SmsCode rules rejected: source=%s count=%d first=%s",
            snapshot.sourceKind.name,
            snapshot.rejectedRules.size,
            snapshot.rejectedRules.first(),
        )
    }
}

package com.github.magisk317.smscode.common.utils

import android.content.Context
import com.github.magisk317.smscode.data.db.DBProvider
import com.github.magisk317.smscode.data.db.entity.SmsCodeRule
import com.github.magisk317.smscode.feature.store.EntityStoreManager
import com.github.magisk317.smscode.feature.store.EntityType
import io.github.magisk317.smscode.domain.model.SmsCodeParseResult
import io.github.magisk317.smscode.domain.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogRefreshResult
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogRepository
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogSnapshot
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleMerger
import io.github.magisk317.smscode.runtime.common.sms.RuntimeSmsCodeAdapter
import io.github.magisk317.smscode.runtime.common.sms.SmsCodeRuleProvider
import io.github.magisk317.smscode.runtime.common.sms.SmsKeywordProvider
import io.github.magisk317.smscode.runtime.common.sms.SmsPackageLabelResolver
import java.util.Locale

object SmsCodeUtils {
    private const val COLUMN_COMPANY = "company"
    private const val COLUMN_KEYWORD = "code_keyword"
    private const val COLUMN_REGEX = "code_regex"

    private val adapter = RuntimeSmsCodeAdapter(
        keywordProvider = SmsKeywordProvider { context, override ->
            override ?: PrefsReader.getSMSCodeKeywords(context).orEmpty()
        },
        ruleProvider = SmsCodeRuleProvider { context ->
            loadMergedRuleSpecs(context)
        },
        labelResolver = SmsPackageLabelResolver { context, label ->
            resolvePackageNameByLabel(context, label)
        },
    )

    suspend fun parseSmsCodeIfExists(context: Context, content: String): String {
        return adapter.parseSmsCodeIfExists(context, content)
    }

    suspend fun parseSmsCodeResultIfExists(context: Context, content: String): SmsCodeParseResult {
        return adapter.parseSmsCodeResultIfExists(context, content)
    }

    @JvmStatic
    fun parseCompany(content: String): String = adapter.parseCompany(content)

    @JvmStatic
    fun parseCompanyCandidates(content: String): List<String> = adapter.parseCompanyCandidates(content)

    fun findPackageNameByLabel(context: Context, label: String?): String? {
        return adapter.findPackageNameByLabel(context, label)
    }

    suspend fun loadOfficialRuleSnapshot(context: Context): SmsCodeRuleCatalogSnapshot {
        return catalogRepository(context).loadOfficialRules().also(::logRejectedOfficialRules)
    }

    suspend fun refreshOfficialRules(context: Context): SmsCodeRuleCatalogRefreshResult {
        return catalogRepository(context).refreshOfficialRules().also { result ->
            result.snapshot?.let(::logRejectedOfficialRules)
            if (!result.success) {
                XLog.w("Refresh official SmsCode rules failed: %s", result.errorMessage ?: "unknown")
            }
        }
    }

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

    @Suppress("TooGenericExceptionCaught")
    private fun queryAllSmsCodeRules(context: Context): List<SmsCodeRule> {
        var rules: List<SmsCodeRule>
        try {
            val smsCodeRuleUri = DBProvider.smsCodeRuleContentUri(context)
            val projection = arrayOf(COLUMN_COMPANY, COLUMN_KEYWORD, COLUMN_REGEX)
            val cursor = context.contentResolver.query(smsCodeRuleUri, projection, null, null, null)
            if (cursor != null) {
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
                cursor.close()
                rules = if (resultRules.isNotEmpty()) {
                    XLog.d("Load SmsCode rules succeed by content provider")
                    resultRules
                } else {
                    loadRulesFromFile(context).also(::logProviderEmptyFallback)
                }
            } else {
                throw IllegalStateException("Cursor is null for URI: $smsCodeRuleUri")
            }
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

    private fun catalogRepository(context: Context): SmsCodeRuleCatalogRepository {
        val appContext = context.applicationContext ?: context
        return SmsCodeRuleCatalogRepository(
            context = appContext,
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

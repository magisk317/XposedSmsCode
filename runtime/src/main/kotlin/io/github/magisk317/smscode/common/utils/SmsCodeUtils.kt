package io.github.magisk317.smscode.common.utils

import android.content.Context
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.data.db.DBProvider
import io.github.magisk317.smscode.db.entity.SmsCodeRule
import io.github.magisk317.smscode.feature.store.EntityStoreManager
import io.github.magisk317.smscode.feature.store.EntityType
import io.github.magisk317.smscode.rule.model.SmsCodeParseResult
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogRefreshResult
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogSnapshot
import io.github.magisk317.smscode.runtime.common.sms.SmsCodeFacade
import io.github.magisk317.smscode.runtime.common.sms.SmsCodeFacadeConfig
import kotlinx.coroutines.flow.Flow

/**
 * Thin host facade; the rule/provider/catalog glue lives in the shared
 * [SmsCodeFacade] (smscode-core runtime) so both hosts stay behaviorally aligned.
 */
object SmsCodeUtils {

    private val facade = SmsCodeFacade(
        SmsCodeFacadeConfig(
            userAgent = "XposedSmsCode/SmsCodeRules",
            sourceUrlPrefKey = PrefConst.KEY_SMS_CODE_RULE_SOURCE_URL,
            keywordReader = { context -> HookPrefsReader.getSMSCodeKeywords(context) },
            userRuleContentUri = { context -> DBProvider.smsCodeRuleContentUri(context) },
            loadUserRulesFromFile = { context ->
                EntityStoreManager.loadEntitiesFromFile(
                    context,
                    EntityType.CODE_RULES,
                    SmsCodeRule::class.java,
                ).map { rule ->
                    SmsCodeRuleSpec(
                        company = rule.company,
                        codeKeyword = rule.codeKeyword,
                        codeRegex = rule.codeRegex,
                    )
                }
            },
            onOfficialRulesRefreshed = { context, _ -> DBProvider.notifyRulesCacheChanged(context) },
        ),
    )

    /**
     * Clears the cached user rules so the next parse re-reads the provider. Intended
     * to be called from the module process when the user edits rules, so changes
     * take effect immediately instead of waiting out the TTL.
     */
    @JvmStatic
    fun invalidateRuleCache() {
        facade.invalidateUserRuleCache()
    }

    /**
     * Drops the cached official-rule snapshot so the next parse reloads from disk /
     * bundled assets. Call after a successful [refreshOfficialRules].
     */
    @JvmStatic
    fun invalidateOfficialRuleCache() {
        facade.invalidateOfficialRuleCache()
    }

    suspend fun parseSmsCodeIfExists(
        context: Context,
        content: String,
        source: SmsCodeParseSource? = null,
        keywordsRegex: String? = null,
    ): String {
        return facade.parseSmsCodeIfExists(
            context,
            content,
            keywordsRegexOverride = keywordsRegex,
            source = source,
        )
    }

    suspend fun parseSmsCodeResultIfExists(
        context: Context,
        content: String,
        source: SmsCodeParseSource? = null,
        keywordsRegex: String? = null,
    ): SmsCodeParseResult {
        return facade.parseSmsCodeResultIfExists(
            context,
            content,
            keywordsRegexOverride = keywordsRegex,
            source = source,
        )
    }

    @JvmStatic
    fun parseCompany(content: String): String = facade.parseCompany(content)

    @JvmStatic
    fun parseCompanyCandidates(content: String): List<String> = facade.parseCompanyCandidates(content)

    fun findPackageNameByLabel(context: Context, label: String?): String? {
        return facade.findPackageNameByLabel(context, label)
    }

    suspend fun loadOfficialRuleSnapshot(context: Context): SmsCodeRuleCatalogSnapshot {
        return facade.loadOfficialRuleSnapshot(context)
    }

    suspend fun refreshOfficialRules(context: Context): SmsCodeRuleCatalogRefreshResult {
        return facade.refreshOfficialRules(context)
    }

    fun observeOfficialRuleSourceUrl(context: Context): Flow<String> =
        facade.observeOfficialRuleSourceUrl(context)

    suspend fun saveOfficialRuleSourceUrl(context: Context, value: String): String {
        return facade.saveOfficialRuleSourceUrl(context, value)
    }
}

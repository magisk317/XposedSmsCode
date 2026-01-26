package com.tianma.xsmscode.common.utils

import android.content.Context
import android.text.TextUtils
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.data.db.DBProvider
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.feature.store.EntityStoreManager
import com.tianma.xsmscode.feature.store.EntityType
import de.robv.android.xposed.XSharedPreferences
import java.util.regex.Pattern

/**
 * 验证码相关Utils
 */
object SmsCodeUtils {

    private const val LEVEL_DIGITAL_6 = 4
    private const val LEVEL_DIGITAL_4 = 3
    private const val LEVEL_DIGITAL_OTHERS = 2
    private const val LEVEL_TEXT = 1
    private const val LEVEL_CHARACTER = 0
    private const val LEVEL_NONE = -1

    /**
     * 是否包含中文
     */
    private fun containsChinese(text: String): Boolean {
        val regex = "[\u4e00-\u9fa5]|。"
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(text)
        return matcher.find()
    }

    /**
     * 解析文本内容中的验证码关键字，如果有则返回第一个匹配到的关键字，否则返回 空字符串
     */
    private fun parseKeyword(keywordsRegex: String, content: String): String {
        val pattern = Pattern.compile(keywordsRegex)
        val matcher = pattern.matcher(content)
        return if (matcher.find()) {
            matcher.group()
        } else {
            ""
        }
    }

    private fun loadCodeKeywordsBySP(context: Context): String? {
        return SPUtils.getSMSCodeKeywords(context)
    }

    private fun loadCodeKeywordsByXSP(): String? {
        val preferences = XSharedPreferences(BuildConfig.APPLICATION_ID, PrefConst.PREF_NAME)
        return XSPUtils.getSMSCodeKeywords(preferences)
    }

    /**
     * 解析文本中的验证码并返回，如果不存在返回空字符
     */
    @JvmStatic
    fun parseSmsCodeIfExists(context: Context, content: String, useXSP: Boolean): String {
        var result = parseByCustomRules(context, content)
        if (TextUtils.isEmpty(result)) {
            result = parseByDefaultRule(context, content, useXSP)
        }
        return result
    }

    /**
     * Parse SMS code by default rule
     */
    private fun parseByDefaultRule(context: Context, content: String, useXSP: Boolean): String {
        var result = ""
        val keywordsRegex = if (useXSP) {
            loadCodeKeywordsByXSP()
        } else {
            loadCodeKeywordsBySP(context)
        } ?: ""
        val keyword = parseKeyword(keywordsRegex, content)
        if (!TextUtils.isEmpty(keyword)) {
            result = if (containsChinese(content)) {
                getSmsCodeCN(keyword, content)
            } else {
                getSmsCodeEN(keyword, content)
            }
        }
        return result
    }

    /**
     * 获取中文短信中包含的验证码
     */
    private fun getSmsCodeCN(keyword: String, content: String): String {
        val codeRegex = "(?<![a-zA-Z0-9])[a-zA-Z0-9]{4,8}(?![a-zA-Z0-9])"
        val handledContent = removeAllWhiteSpaces(content)
        var smsCode = getSmsCode(codeRegex, keyword, handledContent)
        if (TextUtils.isEmpty(smsCode)) {
            smsCode = getSmsCode(codeRegex, keyword, content)
        }
        return smsCode
    }

    /**
     * 获取英文短信包含的验证码
     */
    private fun getSmsCodeEN(keyword: String, content: String): String {
        val codeRegex = "(?<![0-9])[0-9]{4,8}(?![0-9])"
        var smsCode = getSmsCode(codeRegex, keyword, content)
        if (TextUtils.isEmpty(smsCode)) {
            val handledContent = removeAllWhiteSpaces(content)
            smsCode = getSmsCode(codeRegex, keyword, handledContent)
        }
        return smsCode
    }

    /**
     * Remove all white spaces.
     */
    private fun removeAllWhiteSpaces(content: String): String {
        return content.replace("\\s*".toRegex(), "")
    }

    /**
     * Parse SMS code
     */
    private fun getSmsCode(codeRegex: String, keyword: String, content: String): String {
        val p = Pattern.compile(codeRegex)
        val m = p.matcher(content)
        val possibleCodes = mutableListOf<String>()
        while (m.find()) {
            possibleCodes.add(m.group())
        }
        if (possibleCodes.isEmpty()) return ""

        var filteredCodes = possibleCodes.filter { isNearToKeyword(keyword, it, content) }
        if (filteredCodes.isEmpty()) {
            filteredCodes = possibleCodes
        }

        var maxMatchLevel = LEVEL_NONE
        var minDistance = content.length
        var smsCode = ""
        for (filteredCode in filteredCodes) {
            val curLevel = getMatchLevel(filteredCode)
            if (curLevel > maxMatchLevel) {
                maxMatchLevel = curLevel
                minDistance = distanceToKeyword(keyword, filteredCode, content)
                smsCode = filteredCode
            } else if (curLevel == maxMatchLevel) {
                val curDistance = distanceToKeyword(keyword, filteredCode, content)
                if (curDistance < minDistance) {
                    minDistance = curDistance
                    smsCode = filteredCode
                }
            }
        }
        return smsCode
    }

    private fun getMatchLevel(matchedStr: String): Int {
        return when {
            matchedStr.matches("^[0-9]{6}$".toRegex()) -> LEVEL_DIGITAL_6
            matchedStr.matches("^[0-9]{4}$".toRegex()) -> LEVEL_DIGITAL_4
            matchedStr.matches("^[0-9]*$".toRegex()) -> LEVEL_DIGITAL_OTHERS
            matchedStr.matches("^[a-zA-Z]*$".toRegex()) -> LEVEL_CHARACTER
            else -> LEVEL_TEXT
        }
    }

    private fun isNearToKeyword(keyword: String, possibleCode: String, content: String): Boolean {
        return distanceToKeyword(keyword, possibleCode, content) <= 30
    }

    private fun distanceToKeyword(keyword: String, possibleCode: String, content: String): Int {
        val keywordIdx = content.indexOf(keyword)
        val possibleCodeIdx = content.indexOf(possibleCode)
        return Math.abs(keywordIdx - possibleCodeIdx)
    }

    private fun parseByCustomRules(context: Context, content: String): String {
        val rules = queryAllSmsCodeRules(context)
        val lowerContent = content.lowercase()
        for (rule in rules) {
            if (lowerContent.contains(rule.company?.lowercase() ?: "")
                && lowerContent.contains(rule.codeKeyword.lowercase())
            ) {
                val pattern = Pattern.compile(rule.codeRegex)
                val matcher = pattern.matcher(content)
                if (matcher.find()) {
                    return matcher.group()
                }
            }
        }
        return ""
    }

    private fun queryAllSmsCodeRules(context: Context): List<SmsCodeRule> {
        var rules: List<SmsCodeRule>
        try {
            val smsCodeRuleUri = DBProvider.SMS_CODE_RULE_URI
            val resolver = context.contentResolver

            val companyColumn = "company"
            val keywordColumn = "code_keyword"
            val regexColumn = "code_regex"

            val projection = arrayOf(companyColumn, keywordColumn, regexColumn)

            val cursor = resolver.query(smsCodeRuleUri, projection, null, null, null)
            if (cursor != null) {
                val resultRules = mutableListOf<SmsCodeRule>()
                while (cursor.moveToNext()) {
                    val rule = SmsCodeRule(
                        company = cursor.getString(cursor.getColumnIndexOrThrow(companyColumn)),
                        codeKeyword = cursor.getString(cursor.getColumnIndexOrThrow(keywordColumn)),
                        codeRegex = cursor.getString(cursor.getColumnIndexOrThrow(regexColumn))
                    )
                    resultRules.add(rule)
                }
                cursor.close()
                XLog.d("Load SmsCode rules succeed by content provider")
                rules = resultRules
            } else {
                throw Exception("Cursor is null")
            }
        } catch (e: Throwable) {
            rules = EntityStoreManager.loadEntitiesFromFile(
                EntityType.CODE_RULES, SmsCodeRule::class.java
            ) ?: emptyList()
            XLog.d("Load SmsCode rules by file")
        }
        return rules
    }

    @JvmStatic
    fun parseCompany(content: String): String {
        val regex = "((?<=【)(.*?)(?=】))|((?<=\\[)(.*?)(?=\\]))"
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(content)
        val possibleCompanies = mutableListOf<String>()
        while (matcher.find()) {
            possibleCompanies.add(matcher.group())
        }
        val sb = StringBuilder()
        var needSpace = false
        for (company in possibleCompanies) {
            if (needSpace) {
                sb.append(' ')
            } else {
                needSpace = true
            }
            sb.append(company)
        }
        return sb.toString()
    }
}

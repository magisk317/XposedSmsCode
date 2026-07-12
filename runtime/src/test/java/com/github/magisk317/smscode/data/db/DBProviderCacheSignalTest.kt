package com.github.magisk317.smscode.data.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DBProviderCacheSignalTest {

    @Test
    fun prefsCacheContentUriString_usesPackageAuthorityAndPath() {
        val uri = DBProvider.prefsCacheContentUriString("com.example.smscode")

        assertEquals("content://com.example.smscode.db.provider/prefs_cache", uri)
        assertTrue(uri.endsWith("/${DBProvider.PATH_PREFS_CACHE}"))
    }

    @Test
    fun rulesCacheContentUriString_usesPackageAuthorityAndPath() {
        val uri = DBProvider.rulesCacheContentUriString("com.example.smscode")

        assertEquals("content://com.example.smscode.db.provider/rules_cache", uri)
        assertTrue(uri.endsWith("/${DBProvider.PATH_RULES_CACHE}"))
    }

    @Test
    fun cacheSignalUris_differFromDataUris() {
        val packageName = "com.example.smscode"
        val prefs = DBProvider.prefsCacheContentUriString(packageName)
        val rules = DBProvider.rulesCacheContentUriString(packageName)
        val smsRules = "content://$packageName.db.provider/sms_code_rule"

        assertTrue(prefs != rules)
        assertTrue(rules != smsRules)
        assertTrue(prefs.contains("prefs_cache"))
        assertTrue(rules.contains("rules_cache"))
    }
}

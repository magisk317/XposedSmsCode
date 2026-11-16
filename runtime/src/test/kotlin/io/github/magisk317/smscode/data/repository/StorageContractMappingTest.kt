package io.github.magisk317.smscode.data.repository

import io.github.magisk317.smscode.data.db.entity.AppInfo
import io.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.db.entity.SmsCodeRule
import io.github.magisk317.smscode.runtime.RuntimeBackupRule
import io.github.magisk317.smscode.runtime.RuntimeBackupSmsRecord
import io.github.magisk317.smscode.runtime.toBackupRecord
import io.github.magisk317.smscode.runtime.toBackupRule
import io.github.magisk317.smscode.runtime.toRuleEntity
import io.github.magisk317.smscode.runtime.toSmsMsgEntity
import io.github.magisk317.smscode.runtime.bridge.UiAppConfig
import io.github.magisk317.smscode.runtime.common.utils.JsonUtils
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.rule.repository.SmsCodeRuleRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the DTO-to-storage mappings the UI bridge relies on, and the JSON shape of
 * the app-config mirror file: the Xposed hook deserializes that file into the Room
 * entity when its provider is unavailable, so a DTO written by the app process must
 * produce byte-identical JSON.
 */
class StorageContractMappingTest {

    @Test
    fun appConfigMapping_roundTrips() {
        val config = UiAppConfig(packageName = "pkg.a", label = "A", blocked = true)
        assertEquals(config, config.toEntity().toUiConfig())

        val bare = UiAppConfig(packageName = "pkg.b")
        assertEquals(bare, bare.toEntity().toUiConfig())
    }

    @Test
    fun appConfigMirrorFile_keepsHookFallbackJsonShape() {
        val ui = listOf(UiAppConfig(packageName = "pkg.a", label = "A", blocked = true))
        val entity = listOf(AppInfo(packageName = "pkg.a", label = "A", blocked = true))
        assertEquals(
            JsonUtils.listToJson(entity, AppInfo::class.java),
            JsonUtils.listToJson(ui, UiAppConfig::class.java),
        )

        val uiBare = listOf(UiAppConfig(packageName = "pkg.b"))
        val entityBare = listOf(AppInfo(packageName = "pkg.b"))
        assertEquals(
            JsonUtils.listToJson(entityBare, AppInfo::class.java),
            JsonUtils.listToJson(uiBare, UiAppConfig::class.java),
        )

        val json = JsonUtils.listToJson(ui, UiAppConfig::class.java)
        assertTrue(json.contains("\"packageName\""))
        assertTrue(json.contains("\"label\""))
        assertTrue(json.contains("\"blocked\""))
    }

    @Test
    fun backupRuleMapping_roundTrips() {
        val rule = RuntimeBackupRule(
            company = "Acme",
            codeKeyword = "code",
            codeRegex = "\\d{6}",
        )
        assertEquals(rule, rule.toRuleEntity().toBackupRule())
    }

    @Test
    fun ruleRecordMapping_roundTrips() {
        val record = SmsCodeRuleRecord(
            id = 7L,
            spec = SmsCodeRuleSpec(
                company = "Acme",
                codeKeyword = "code",
                codeRegex = "\\d{6}",
            ),
        )
        assertEquals(record, record.toEntity().toRecord())
        assertEquals(7L, record.toEntity().id)

        val fresh = SmsCodeRuleRecord(
            id = 0L,
            spec = SmsCodeRuleSpec(company = null, codeKeyword = "k", codeRegex = "r"),
        )
        assertNull(fresh.toEntity().id)
        assertEquals(fresh, fresh.toEntity().toRecord())
    }

    @Test
    fun backupRecordMapping_roundTrips() {
        val record = RuntimeBackupSmsRecord(
            sender = "10086",
            body = "Your code is 123456",
            date = 111L,
            processedTime = 222L,
            company = "Acme",
            smsCode = "123456",
            packageName = "pkg.a",
            simSlot = 1,
            subId = 2,
            msgType = SmsMsg.MSG_TYPE_SMS,
            callType = 0,
        )
        assertEquals(record, record.toSmsMsgEntity().toBackupRecord())
    }
}

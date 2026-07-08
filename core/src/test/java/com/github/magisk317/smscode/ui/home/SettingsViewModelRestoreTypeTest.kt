package com.github.magisk317.smscode.ui.home

import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.constant.PrefValueType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsViewModelRestoreTypeTest {

    @Test
    fun coerceRestoreValue_booleanValues_areParsed() {
        val one = SettingsViewModel.coerceRestoreValue(PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, "1")
        val trueValue = SettingsViewModel.coerceRestoreValue(PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, "true")
        val falseValue = SettingsViewModel.coerceRestoreValue(PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, "false")

        assertEquals(PrefValueType.BOOLEAN, one.type)
        assertTrue(one.shouldWrite)
        assertEquals(true, one.booleanValue)

        assertTrue(trueValue.shouldWrite)
        assertEquals(true, trueValue.booleanValue)

        assertTrue(falseValue.shouldWrite)
        assertEquals(false, falseValue.booleanValue)
    }

    @Test
    fun coerceRestoreValue_invalidBoolean_isSkipped() {
        val invalid = SettingsViewModel.coerceRestoreValue(PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY, "abc")
        assertEquals(PrefValueType.BOOLEAN, invalid.type)
        assertFalse(invalid.shouldWrite)
    }

    @Test
    fun coerceRestoreValue_invalidNumberValues_areSkipped() {
        val invalidInt = SettingsViewModel.coerceRestoreValue(PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS, "not_int")

        assertEquals(PrefValueType.INT, invalidInt.type)
        assertFalse(invalidInt.shouldWrite)
    }

    @Test
    fun coerceRestoreValue_unknownKey_usesStringPath() {
        val value = SettingsViewModel.coerceRestoreValue("unknown_pref_key", "raw")
        assertEquals(PrefValueType.STRING, value.type)
        assertTrue(value.shouldWrite)
        assertEquals("raw", value.stringValue)
    }
}

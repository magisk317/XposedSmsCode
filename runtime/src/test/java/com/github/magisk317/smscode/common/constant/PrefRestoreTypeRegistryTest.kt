package com.github.magisk317.smscode.common.constant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrefRestoreTypeRegistryTest {

    @Test
    fun typeOf_recordSplitKeys_areBoolean() {
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_CODE_RECORDS_CODE))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY))
    }

    @Test
    fun typeOf_legacyBooleanKeys_areBoolean() {
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_HIDE_LAUNCHER_ICON))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_ANALYTICS))
    }

    @Test
    fun typeOf_runtimeLogRetentionDays_isInt() {
        assertEquals(PrefValueType.INT, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS))
    }

    @Test
    fun typeOf_unknownKey_defaultsToString() {
        assertEquals(PrefValueType.STRING, PrefRestoreTypeRegistry.typeOf("unknown_pref_key"))
    }
}

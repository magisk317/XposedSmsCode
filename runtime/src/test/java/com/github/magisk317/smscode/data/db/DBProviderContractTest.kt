package com.github.magisk317.smscode.data.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DBProviderContractTest {
    @Test
    fun isSupportedDateSortOrder_acceptsOnlyDateSortsWithOptionalLimit() {
        assertTrue(DBProvider.Contract.isSupportedDateSortOrder(null))
        assertTrue(DBProvider.Contract.isSupportedDateSortOrder(""))
        assertTrue(DBProvider.Contract.isSupportedDateSortOrder("date desc"))
        assertTrue(DBProvider.Contract.isSupportedDateSortOrder("DATE  ASC LIMIT 20"))

        assertFalse(DBProvider.Contract.isSupportedDateSortOrder("processed_time desc"))
        assertFalse(DBProvider.Contract.isSupportedDateSortOrder("date desc limit 0"))
        assertFalse(DBProvider.Contract.isSupportedDateSortOrder("date desc; drop table sms_msg"))
    }

    @Test
    fun isProjectionSupported_rejectsUnknownColumns() {
        assertTrue(DBProvider.Contract.isProjectionSupported(null, DBProvider.SMS_MSG_COLUMNS))
        assertTrue(DBProvider.Contract.isProjectionSupported(arrayOf("_id", "date", "body"), DBProvider.SMS_MSG_COLUMNS))

        assertFalse(DBProvider.Contract.isProjectionSupported(arrayOf("_id", "unknown"), DBProvider.SMS_MSG_COLUMNS))
    }

    @Test
    fun normalizeAutoInputEvent_passesThroughValidValues() {
        val normalized = DBProvider.Contract.normalizeAutoInputEvent(
            id = 42L,
            recordId = 7L,
            packageName = "com.example.app",
            codeLength = 6,
            attemptAt = 1_000L,
            maxPackageNameLength = 255,
            now = 9_999L,
        )

        assertEquals(42L, normalized.id)
        assertEquals(7L, normalized.recordId)
        assertEquals("com.example.app", normalized.packageName)
        assertEquals(6, normalized.codeLength)
        assertEquals(1_000L, normalized.attemptAt)
    }

    @Test
    fun normalizeAutoInputEvent_nonPositiveIdBecomesNullForAutoGenerate() {
        assertNull(
            DBProvider.Contract.normalizeAutoInputEvent(
                id = 0L,
                recordId = null,
                packageName = null,
                codeLength = 4,
                attemptAt = 5L,
                maxPackageNameLength = 255,
                now = 9_999L,
            ).id,
        )
        assertNull(
            DBProvider.Contract.normalizeAutoInputEvent(
                id = -1L,
                recordId = null,
                packageName = null,
                codeLength = 4,
                attemptAt = 5L,
                maxPackageNameLength = 255,
                now = 9_999L,
            ).id,
        )
    }

    @Test
    fun normalizeAutoInputEvent_clampsCodeLengthAndFallsBackAttemptAt() {
        val normalized = DBProvider.Contract.normalizeAutoInputEvent(
            id = null,
            recordId = null,
            packageName = null,
            codeLength = -3,
            attemptAt = 0L,
            maxPackageNameLength = 255,
            now = 9_999L,
        )

        assertEquals(0, normalized.codeLength)
        assertEquals(9_999L, normalized.attemptAt)
        assertNull(normalized.recordId)
        assertNull(normalized.packageName)
    }

    @Test
    fun normalizeAutoInputEvent_defaultsNullCodeLengthToZero() {
        val normalized = DBProvider.Contract.normalizeAutoInputEvent(
            id = null,
            recordId = null,
            packageName = null,
            codeLength = null,
            attemptAt = null,
            maxPackageNameLength = 255,
            now = 9_999L,
        )

        assertEquals(0, normalized.codeLength)
        assertEquals(9_999L, normalized.attemptAt)
    }

    @Test
    fun normalizeAutoInputEvent_truncatesPackageNameToMaxLength() {
        val longName = "x".repeat(300)
        val normalized = DBProvider.Contract.normalizeAutoInputEvent(
            id = null,
            recordId = null,
            packageName = longName,
            codeLength = 6,
            attemptAt = 100L,
            maxPackageNameLength = 255,
            now = 9_999L,
        )

        assertEquals(255, normalized.packageName?.length)
    }
}

package com.github.magisk317.smscode.data.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

class RuntimeLogProviderPolicyTest {
    @Test
    fun ingressFieldsAreBoundedWithoutSplittingUtf8Characters() {
        val input = "code-" + "\u9a8c".repeat(10_000)
        val result = RuntimeLogIngressPolicy.truncateUtf8(input, 101)

        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 101)
        assertTrue(input.startsWith(result))
        assertEquals("I", RuntimeLogIngressPolicy.normalizeLevel("unexpected"))
        assertEquals("W", RuntimeLogIngressPolicy.normalizeLevel("w"))
    }

    @Test
    fun persistentQuotaPrunesOldestRuntimeLogsOnly() {
        val dir = Files.createTempDirectory("runtime-log-quota").toFile()
        try {
            val runtimeLog = File(dir, "runtime.jsonl")
            val unrelatedLog = File(dir, "crash.log").apply { writeText("keep") }
            RandomAccessFile(runtimeLog, "rw").use {
                it.setLength(RuntimeLogIngressPolicy.MAX_PERSISTED_LOG_BYTES)
            }
            assertTrue(RuntimeLogIngressPolicy.ensurePersistentQuota(dir))
            assertFalse(runtimeLog.exists())
            assertTrue(unrelatedLog.exists())
            assertTrue(RuntimeLogIngressPolicy.ensurePersistentQuota(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}

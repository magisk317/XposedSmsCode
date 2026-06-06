package com.github.magisk317.smscode.common.utils

import io.github.magisk317.smscode.runtime.common.utils.FrameworkCompatibilityMonitor
import io.github.magisk317.smscode.runtime.common.utils.FrameworkInfo
import io.github.magisk317.smscode.runtime.common.utils.FrameworkInfoResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FrameworkCompatibilityMonitorTest {

    @Test
    fun detectIssue_allowsPreviouslyKnownIncompatibleFramework() {
        val frameworkInfo = FrameworkInfo(
            name = "Vector",
            version = "2.0 (3021)",
            moduleId = "zygisk_vector",
            author = "JingMatrix",
            source = FrameworkInfo.Source.MODULE_PROP,
        )

        val issue = FrameworkCompatibilityMonitor.detectIssue(
            frameworkInfo = frameworkInfo,
            latestLogMessage = null,
            detectedAt = 123L,
        )

        assertNull(issue)
    }

    @Test
    fun detectIssue_returnsAnnotationIssueWhenKnownLogAppears() {
        val issue = FrameworkCompatibilityMonitor.detectIssue(
            frameworkInfo = null,
            latestLogMessage = "Hooker should be annotated with @XposedHooker",
            detectedAt = 456L,
        )

        assertNotNull(issue)
        assertEquals(
            FrameworkCompatibilityMonitor.FrameworkIssueType.HOOKER_ANNOTATION_INCOMPATIBLE,
            issue?.issueType,
        )
    }

    @Test
    fun parseModuleProp_parsesVectorModuleInfo() {
        val info = FrameworkInfoResolver.parseModuleProp(
            content = """
                id=zygisk_vector
                name=Vector
                version=v2.0 (3021)
                versionCode=3021
                author=JingMatrix
            """.trimIndent(),
            path = "/data/adb/modules/zygisk_vector/module.prop",
        )

        assertNotNull(info)
        assertEquals("Vector", info?.name)
        assertEquals("2.0 (3021)", info?.version)
        assertEquals("zygisk_vector", info?.moduleId)
        assertEquals("JingMatrix", info?.author)
    }

    @Test
    fun detectIssue_returnsNullWhenNothingMatches() {
        val issue = FrameworkCompatibilityMonitor.detectIssue(
            frameworkInfo = FrameworkInfo(
                name = "LSPosed IT (GitHub@magisk317)",
                version = "2.0.1-it (7649)",
                moduleId = "zygisk_lsposed",
                author = "LSPosed Developers",
                source = FrameworkInfo.Source.MODULE_PROP,
            ),
            latestLogMessage = null,
            detectedAt = 789L,
        )

        assertNull(issue)
    }

    @Test
    fun detectIssue_allowsJingMatrixFrameworkWhenNoRuntimeErrorAppears() {
        val issue = FrameworkCompatibilityMonitor.detectIssue(
            frameworkInfo = FrameworkInfo(
                name = "DreamLand",
                version = "2.0",
                moduleId = "dreamland_jingmatrix",
                author = "JingMatrix",
                source = FrameworkInfo.Source.MODULE_PROP,
            ),
            latestLogMessage = null,
            detectedAt = 101L,
        )

        assertNull(issue)
    }
}

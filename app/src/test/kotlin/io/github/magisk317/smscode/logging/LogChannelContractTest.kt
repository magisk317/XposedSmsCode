package io.github.magisk317.smscode.logging

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogChannelContractTest {


    @Test
    fun `app and hook derive diagnostics from debug or CI only`() {
        val sources = listOf(
            projectFile("app/src/main/kotlin/io/github/magisk317/smscode/ui/app/SmsCodeApplication.kt"),
            projectFile("hook/src/main/kotlin/io/github/magisk317/smscode/xp/XposedRuntimeInstaller.kt"),
        ).joinToString("\n") { it.readText() }

        val diagnosticExpression = "isDiagnosticBuild = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD"
        assertEquals(2, sources.split(diagnosticExpression).size - 1)
        assertFalse(sources.contains("ALLOW_" + "PLAINTEXT_LOGS"))
        assertFalse(sources.contains("allow" + "PlaintextLogs"))
    }

    @Test
    fun `CI AAB intentionally uses release shape with diagnostic logging`() {
        val yaml = projectFile(".gitlab-ci.yml").readText()
        val job = yaml.substringAfter("android:ci:aab:").substringBefore("android:ci:publish-play:")

        assertTrue(job.contains("MAGISK_IS_CI_BUILD: \"true\""))
        assertTrue(job.contains(":app:bundlePlayRelease"))
        assertTrue(job.contains("signed, non-debuggable AAB"))
    }

    @Test
    fun `public release strips every owned Android log surface`() {
        val rules = projectFile("app/proguard-release.pro").readText()

        listOf("void v", "void d", "void i", "void w", "void e", "void log", "void structured").forEach {
            assertTrue(rules.contains(it), "Missing release XLog rule: $it")
        }
        assertTrue(rules.contains("class android.util.Log"))
        assertTrue(rules.contains("class io.github.magisk317.xposed.logging.TaggedLog"))
        assertTrue(rules.contains("int println"))
    }

    @Test
    fun `SMS parser never logs successful sender body or code plaintext`() {
        val source = projectFile(
            "smscode/core/runtime/src/main/java/io/github/magisk317/smscode/runtime/verification/SmsParseAction.kt",
        ).readText()

        assertFalse(source.contains("Sender" + ": %s"))
        assertFalse(source.contains("Body" + ": %s"))
        assertFalse(source.contains("Diag SMS " + "body"))
        assertFalse(source.contains("code=%s, " + "body=%s"))
    }

    @Test
    fun `record export warning names every plaintext field`() {
        val warnings = listOf(
            projectFile("presentation/src/main/res/values/strings.xml").readText() to
                listOf("sender", "full SMS body", "verification code in plaintext"),
            projectFile("presentation/src/main/res/values-zh-rCN/strings.xml").readText() to
                listOf("发件人", "完整短信正文", "验证码原文"),
            projectFile("presentation/src/main/res/values-zh-rTW/strings.xml").readText() to
                listOf("發件人", "完整簡訊正文", "驗證碼原文"),
        )

        warnings.forEach { (source, requiredText) ->
            assertTrue(source.contains("record_export_current_tab_option"))
            requiredText.forEach { assertTrue(source.contains(it), "Missing export warning text: $it") }
        }
    }

    @Test
    fun `release does not install hook log clients or sinks`() {
        val hook = projectFile(
            "hook/src/main/kotlin/io/github/magisk317/smscode/xp/XposedRuntimeInstaller.kt",
        ).readText()
        val provider = projectFile(
            "runtime/src/main/kotlin/io/github/magisk317/smscode/data/log/RuntimeLogProvider.kt",
        ).readText()

        assertTrue(hook.contains("if (logPolicy.loggingEnabled)"))
        assertTrue(hook.contains("if (policy.loggingEnabled)"))
        assertTrue(provider.contains("if (!BuildConfig.DEBUG && !BuildConfig.IS_CI_BUILD) return"))
    }

    @Test
    fun `production code never re-enables the in-memory log test seam`() {
        val seamCall = Regex("\\.\\s*(setTestSink|setLogPrinterForTest)\\s*\\(")
        val offenders = productionSources().filter { source ->
            seamCall.containsMatchIn(source.readText())
        }.map { it.relativeTo(projectRoot()).invariantSeparatorsPath }

        assertTrue(
            offenders.isEmpty(),
            "A test sink bypasses the release log gate, so production code must not install one: $offenders",
        )
    }

    private fun projectRoot(): File = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun productionSources(): List<File> {
        val skipped = setOf("build", ".gradle", ".kotlin", "node_modules")
        return projectRoot().walkTopDown()
            .onEnter { dir -> dir.name !in skipped && !dir.name.startsWith(".") }
            .filter { it.isFile && it.extension in setOf("kt", "java") && it.invariantSeparatorsPath.contains("/src/main") }
            .toList()
    }

    private fun projectFile(relativePath: String): File {
        return generateSequence(File(".").canonicalFile) { it.parentFile }
            .map { root -> File(root, relativePath) }
            .firstOrNull(File::isFile)
            ?: error("Cannot resolve project file: $relativePath")
    }
}

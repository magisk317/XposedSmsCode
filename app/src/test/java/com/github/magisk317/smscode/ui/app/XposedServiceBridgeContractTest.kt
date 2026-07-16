package com.github.magisk317.smscode.ui.app

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XposedServiceBridgeContractTest {
    @Test
    fun `phone processes restart only after xposed service bind succeeds`() {
        val bridgeSource = projectFile(
            "app/src/main/java/com/github/magisk317/smscode/ui/app/XposedServiceBridge.kt",
        ).readText()
        val beforeBind = bridgeSource.substringBefore("override fun onServiceBind")
        val bindCallback = bridgeSource
            .substringAfter("override fun onServiceBind")
            .substringBefore("override fun onServiceDied")
        val diedCallback = bridgeSource.substringAfter("override fun onServiceDied")
        val handleBindCall = "application.handleXposedServiceBound"
        val restartCall = "PhoneProcessRestartCoordinator.requestAfterXposedServiceBind"

        assertFalse(restartCall in beforeBind)
        assertEquals(1, bindCallback.windowed(restartCall.length).count { it == restartCall })
        assertTrue(handleBindCall in bindCallback)
        assertTrue(bindCallback.indexOf(handleBindCall) < bindCallback.indexOf(restartCall))
        assertFalse(restartCall in diedCallback)
    }

    @Test
    fun `application startup and package replacement cannot restart phone processes`() {
        val applicationSource = projectFile(
            "app/src/main/java/com/github/magisk317/smscode/ui/app/SmsCodeApplication.kt",
        ).readText()
        val coordinatorSource = projectFile(
            "app/src/main/java/com/github/magisk317/smscode/ui/app/PhoneProcessRestartCoordinator.kt",
        ).readText()
        val productionManifests = productionAppFiles("AndroidManifest.xml")

        assertFalse("PhoneProcessRestartCoordinator." in applicationSource)
        assertFalse("fun restartAfterInstallOrUpdate" in coordinatorSource)
        assertTrue(productionManifests.isNotEmpty())
        productionManifests.forEach { manifest ->
            val manifestSource = manifest.readText()
            assertFalse("PackageReplacedReceiver" in manifestSource, manifest.path)
            assertFalse("android.intent.action.MY_PACKAGE_REPLACED" in manifestSource, manifest.path)
        }
        assertFalse(
            projectPath(
                "app/src/main/java/com/github/magisk317/smscode/receiver/PackageReplacedReceiver.kt",
            ).exists(),
        )
    }

    @Test
    fun `production app code exposes exactly one phone process restart entrypoint`() {
        val productionSources = productionAppFiles(".kt", ".java")
        val serviceBindCall = "PhoneProcessRestartCoordinator.requestAfterXposedServiceBind("
        val legacyAsyncCall = "requestAfterInstallOrUpdate("
        val legacyBlockingCall = "restartAfterInstallOrUpdate("

        val coordinatorReferences = productionSources.filter {
            "PhoneProcessRestartCoordinator" in it.readText()
        }
        assertEquals(2, coordinatorReferences.size)
        assertEquals(
            setOf("PhoneProcessRestartCoordinator.kt", "XposedServiceBridge.kt"),
            coordinatorReferences.map { it.name }.toSet(),
        )
        assertTrue(productionSources.none { it.name == "PackageReplacedReceiver.kt" })

        val serviceBindCallers = productionSources.filter { serviceBindCall in it.readText() }
        assertEquals(1, serviceBindCallers.size)
        assertTrue(
            serviceBindCallers.single().invariantSeparatorsPath.endsWith(
                "/ui/app/XposedServiceBridge.kt",
            ),
        )

        val legacyAsyncCallers = productionSources.filter { legacyAsyncCall in it.readText() }
        assertEquals(1, legacyAsyncCallers.size)
        assertTrue(
            legacyAsyncCallers.single().invariantSeparatorsPath.endsWith(
                "/ui/app/PhoneProcessRestartCoordinator.kt",
            ),
        )
        assertTrue(productionSources.none { legacyBlockingCall in it.readText() })
    }

    private fun projectFile(relativePath: String): File {
        val file = projectPath(relativePath)
        require(file.isFile) { "Cannot resolve file: $relativePath" }
        return file
    }

    private fun projectPath(relativePath: String): File {
        val direct = File(relativePath)
        return if (direct.exists() || File("settings.gradle.kts").isFile) {
            direct
        } else {
            File("../$relativePath")
        }
    }

    private fun productionAppFiles(vararg suffixes: String): List<File> {
        val ignoredSourceSets = setOf("test", "androidTest", "testFixtures")
        return projectPath("app/src")
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name !in ignoredSourceSets }
            .flatMap { sourceSet ->
                sourceSet.walkTopDown()
                    .filter { file -> file.isFile && suffixes.any(file.name::endsWith) }
                    .toList()
            }
    }
}

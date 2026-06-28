package com.github.magisk317.smscode.xp.hook.mms

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MmsMessagesHookContractTest {

    @Test
    fun `conflict suppression notifies when plugin context is available`() {
        val source = resolveProjectFile(
            "app/src/main/java/com/github/magisk317/smscode/xp/hook/mms/MmsMessagesHook.kt",
        ).readText()

        val method = source.substringAfter("private fun maybeBlock")
            .substringBefore("private fun scheduleBlacklistDelete")
        val suppressIndex = method.indexOf("ModuleConflictArbiter.shouldSuppressByRelay")
        val pluginGuardIndex = method.indexOf("if (pluginContext != null)")
        val notifyIndex = method.indexOf("RelayConflictNoticeHelper.notifyConflictOnSms")

        assertTrue(suppressIndex >= 0, "MMS conflict suppression guard must remain")
        assertTrue(pluginGuardIndex >= 0, "MMS conflict notification must guard missing plugin context")
        assertTrue(notifyIndex >= 0, "MMS conflict suppression must notify the user")
        assertTrue(
            suppressIndex < pluginGuardIndex && pluginGuardIndex < notifyIndex,
            "MMS conflict notification must be sent from the suppression branch after context is available",
        )
    }

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val parent = File("../$relativePath")
        require(parent.isFile) { "Cannot resolve file: $relativePath" }
        return parent
    }
}

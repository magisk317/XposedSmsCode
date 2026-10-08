package io.github.magisk317.smscode.xp.hook.mms

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MmsMessagesHookContractTest {

    @Test
    fun `conflict suppression notifies when plugin context is available`() {
        val source = resolveProjectFile(
            "hook/src/main/kotlin/io/github/magisk317/smscode/xp/hook/mms/MmsMessagesHook.kt",
        ).readText()

        // The hook is now a thin binding over the shared entry-point hook, so the
        // behaviour to protect is the binding itself: relay arbitration and the
        // conflict notification must both stay wired.
        val suppressIndex = source.indexOf("ModuleConflictArbiter.shouldSuppressByRelay")
        val notifyIndex = source.indexOf("RelayConflictNoticeHelper.notifyConflictOnSms")

        assertTrue(suppressIndex >= 0, "MMS conflict suppression guard must remain")
        assertTrue(notifyIndex >= 0, "MMS conflict suppression must notify the user")
    }

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val parent = File("../$relativePath")
        require(parent.isFile) { "Cannot resolve file: $relativePath" }
        return parent
    }
}

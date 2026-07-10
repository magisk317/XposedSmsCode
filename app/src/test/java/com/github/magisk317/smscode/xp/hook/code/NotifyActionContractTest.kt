package com.github.magisk317.smscode.xp.hook.code

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NotifyActionContractTest {

    @Test
    fun `app owned notifications defer channel checks to app receiver`() {
        val source = resolveProjectFile(
            "hook/src/main/java/com/github/magisk317/smscode/xp/hook/code/action/impl/NotifyAction.kt",
        ).readText()

        assertTrue("CodeNotificationDeliveryHelper.requestAppOwnedNotification" in source)
        assertTrue("summary = \"deferred_to_receiver\"" in source)
        assertTrue("channelInitializer = { context -> ensureNotificationChannel(context) }" in source)
    }

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val parent = File("../$relativePath")
        require(parent.isFile) { "Cannot resolve file: $relativePath" }
        return parent
    }
}

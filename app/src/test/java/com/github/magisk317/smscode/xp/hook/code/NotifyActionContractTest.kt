package com.github.magisk317.smscode.xp.hook.code

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NotifyActionContractTest {

    @Test
    fun `app owned fallback delegates posting to the shared runtime and app receiver`() {
        val source = resolveProjectFile(
            "hook/src/main/java/com/github/magisk317/smscode/xp/hook/code/action/impl/NotifyAction.kt",
        ).readText()

        assertTrue("CodeNotificationDeliveryHelper.requestAppOwnedNotification" in source)
        assertTrue("intentFactory = CodeNotificationBroadcastContract::createIntent" in source)
        assertTrue("PhoneOwnedNotificationDispatcher.isPackageAllowedToPost" in source)
    }

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val parent = File("../$relativePath")
        require(parent.isFile) { "Cannot resolve file: $relativePath" }
        return parent
    }
}

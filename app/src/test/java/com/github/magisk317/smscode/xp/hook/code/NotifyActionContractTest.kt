package com.github.magisk317.smscode.xp.hook.code

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NotifyActionContractTest {

    @Test
    fun `phone owned notifications use fixed phone owner channel`() {
        val source = resolveProjectFile(
            "app/src/main/java/com/github/magisk317/smscode/xp/hook/code/action/impl/NotifyAction.kt",
        ).readText()

        assertTrue("PACKAGE_PHONE = \"com.android.phone\"" in source)
        assertTrue("fun resolvePhoneOwnerContext(): Context?" in source)
        assertTrue("mPhoneContext.createPackageContext(" in source)
        assertTrue("ensurePhoneNotificationChannel(phoneOwnerContext)" in source)
        assertTrue("channelId = NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION" in source)
        assertTrue("phoneContext = phoneOwnerContext" in source)
    }

    @Test
    fun `app owned notifications defer channel checks to app receiver`() {
        val source = resolveProjectFile(
            "app/src/main/java/com/github/magisk317/smscode/xp/hook/code/action/impl/NotifyAction.kt",
        ).readText()

        assertTrue("appOwnedChannelInitializer = {}" in source)
        assertTrue("summary = \"deferred_to_receiver\"" in source)
    }

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val parent = File("../$relativePath")
        require(parent.isFile) { "Cannot resolve file: $relativePath" }
        return parent
    }
}

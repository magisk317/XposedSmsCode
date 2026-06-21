package com.github.magisk317.smscode.xp.hook.code

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NotifyActionContractTest {

    @Test
    fun `phone owned notifications reuse mms host message channels`() {
        val source = resolveProjectFile(
            "app/src/main/java/com/github/magisk317/smscode/xp/hook/code/action/impl/NotifyAction.kt",
        ).readText()

        assertTrue("fun resolvePhoneOwnedChannelId()" in source)
        assertTrue("PACKAGE_MMS = \"com.android.mms\"" in source)
        assertTrue("MMS_MESSAGE_CHANNEL_PREFIX = \"Channel_Msg_Default\"" in source)
        assertTrue("MMS_MESSAGE_CHANNEL_GROUP = \"Channel_Msg_Group\"" in source)
        assertTrue("MMS_DEFAULT_CHANNEL_ID = \"Mms_Default\"" in source)
        assertTrue("channelId = resolvePhoneOwnedChannelId()" in source)
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

package com.github.magisk317.smscode.xp.hook.code

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RecordSmsActionContractTest {

    @Test
    fun `record action uses incoming message type for notification code records`() {
        val source = resolveProjectFile(
            "app/src/main/java/com/github/magisk317/smscode/xp/hook/code/action/impl/RecordSmsAction.kt",
        ).readText()

        assertTrue("enabled = enabled ?: recordEnabledForMessageType(mSmsMsg)" in source)
        assertTrue("SmsMsg.MSG_TYPE_APP_NOTIFY -> PrefsReader.recordAppNotifyEnabled" in source)
        assertTrue("SmsMsg.MSG_TYPE_CALL_NOTIFY -> PrefsReader.recordCallNotifyEnabled" in source)
        assertTrue("val selectionArgs = arrayOf(smsMsg.msgType.toString())" in source)
        assertTrue("msgType = smsMsg.msgType" in source)
    }

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val parent = File("../$relativePath")
        require(parent.isFile) { "Cannot resolve file: $relativePath" }
        return parent
    }
}

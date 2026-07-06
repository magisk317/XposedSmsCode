package com.github.magisk317.smscode.receiver

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NmsForwardReceiverContractTest {

    @Test
    fun `manifest exposes nms forward receiver for notification hook action`() {
        val manifest = resolveProjectFile("app/src/main/AndroidManifest.xml").readText()
        val receiverIndex = manifest.indexOf("io.github.magisk317.relay.platform.ipc.ForwardReceiver")
        val actionIndex = manifest.indexOf("com.github.magisk317.smscode.ACTION_FORWARD_SMS", receiverIndex)

        assertTrue(receiverIndex >= 0, "NMS forward receiver must be declared for explicit hook broadcasts")
        assertTrue(actionIndex > receiverIndex, "NMS forward receiver must advertise ACTION_FORWARD_SMS")
    }

    @Test
    fun `nms forward receiver translates shared hook payload to code notification payload`() {
        val source = resolveProjectFile(
            "app/src/main/java/io/github/magisk317/relay/platform/ipc/ForwardReceiver.kt",
        ).readText()

        assertTrue("NotificationHookConst.ACTION_FORWARD_SMS" in source)
        assertTrue("EXTRA_SMS_CODE_LEGACY = \"smsCode\"" in source)
        assertTrue("CodeNotificationPayload.EXTRA_SMS_CODE" in source)
        assertTrue("SmsCodeActionDispatcher.dispatchParsedSmsActions" in source)
        assertTrue("MSG_TYPE_APP_NOTIFY" in source)
        assertTrue("recordAppNotifyEnabled" in source)
        assertTrue("operateSmsDelays = emptyList()" in source)
    }

    @Test
    fun `nms forward receiver logs entry skip dispatch and handled stages`() {
        val source = resolveProjectFile(
            "app/src/main/java/io/github/magisk317/relay/platform/ipc/ForwardReceiver.kt",
        ).readText()

        assertTrue("NmsForwardReceiver received" in source)
        assertTrue("NmsForwardReceiver skipped: missing smsCode" in source)
        assertTrue("NmsForwardReceiver skipped: module disabled" in source)
        assertTrue("NmsForwardReceiver dispatch local actions" in source)
        assertTrue("NmsForwardReceiver handled" in source)
        assertTrue("NmsForwardReceiver finished skip" in source)
        assertTrue("LogRoute.NMS_HOOK" in source)
    }

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val parent = File("../$relativePath")
        require(parent.isFile) { "Cannot resolve file: $relativePath" }
        return parent
    }
}

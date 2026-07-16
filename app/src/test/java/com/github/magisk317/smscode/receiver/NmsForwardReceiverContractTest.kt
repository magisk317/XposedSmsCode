package com.github.magisk317.smscode.receiver

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import com.github.magisk317.smscode.ui.app.AppIpcTokenStore
import com.github.magisk317.smscode.ui.app.AppShellRuntimeBridge
import io.github.magisk317.relay.platform.ipc.ForwardActionDispatcher
import io.github.magisk317.relay.platform.ipc.ForwardOrderedResultFinisher
import io.github.magisk317.relay.platform.ipc.ForwardReceiver
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.xposed.hook.notification.NotificationHookConst
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.smscode.xposed.runtime.CoreRuntimeAccess
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
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

    @Test
    fun `valid nms payload crosses app plan creation without hook process runtime`() = runBlocking {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
        try {
            installCoreRuntime()
            AppIpcTokenStore.ensurePublished(
                existingToken = { "receiver-token" },
                generateToken = { error("existing token should be reused") },
                persistToken = { error("existing token should not be persisted") },
            )
            val context = mockk<Context>()
            val sharedPreferences = mockk<SharedPreferences>()
            every { context.applicationContext } returns context
            every { context.getSharedPreferences("xposed_prefs", Context.MODE_PRIVATE) } returns sharedPreferences
            every { sharedPreferences.contains(any()) } returns false
            AppShellRuntimeBridge.install(context)

            val intent = mockk<Intent>()
            every { intent.action } returns NotificationHookConst.ACTION_FORWARD_SMS
            every { intent.getStringExtra(any()) } answers {
                when (firstArg<String>()) {
                    "ipc_token" -> "receiver-token"
                    "event_id" -> "event-1"
                    "packageName" -> "com.example.bank"
                    "msgType" -> "app_notify"
                    "smsCode" -> "123456"
                    "body" -> "Your code is 123456"
                    "company" -> "Example Bank"
                    else -> null
                }
            }
            every { intent.getLongExtra("date", 0L) } returns 123_456L

            var capturedPlan: SmsCodePostParseCoordinator.ParsedSmsPlan? = null
            val receiver = ForwardReceiver().apply {
                orderedBroadcastProvider = { false }
                orderedResultFinisher = ForwardOrderedResultFinisher { _, _, _ -> false }
                actionDispatcher = ForwardActionDispatcher { _, _, _, plan ->
                    capturedPlan = plan
                    true
                }
            }

            receiver.handleForward(context, intent)

            assertNotNull(capturedPlan)
            assertNotNull(capturedPlan?.notificationPlan)
            assertTrue(capturedPlan?.shouldRecord == true)
            assertSame(RuntimePrefsFacade, HookRuntimeBridge.prefsAccess)
            assertTrue(HookRuntimeBridge.hookProcessInit == null)
        } finally {
            unmockkStatic(SystemClock::class)
        }
    }

    private fun installCoreRuntime() {
        CoreRuntime.install(object : CoreRuntimeAccess {
            override val logTag = "NmsForwardReceiverTest"
            override val logLevel = 2
            override val logToXposed = false
            override val debug = true
            override val applicationId = "com.github.tianma8023.xposed.smscode"
            override val actionNamespace = "com.github.magisk317.smscode"
        })
    }

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val parent = File("../$relativePath")
        require(parent.isFile) { "Cannot resolve file: $relativePath" }
        return parent
    }
}

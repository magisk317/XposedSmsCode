package com.github.magisk317.smscode.xp.hook.code

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SmsHandlerHookContractTest {

    @Test
    fun `dispatch intent marks handled only after runtime contexts are available`() {
        val source = resolveProjectFile(
            "hook/src/main/java/com/github/magisk317/smscode/xp/hook/code/SmsHandlerHook.kt",
        ).readText()

        val method = source.substringAfter("private fun beforeDispatchIntentHandler")
            .substringBefore("private fun scheduleBlacklistDelete")
        val contextGuardIndex = method.indexOf("if (pluginContext == null || phoneContext == null)")
        val runtimeRecoveryIndex = method.indexOf("ensureRuntimeForDispatch(param, receiverIndex)")
        val sharedDedupIndex = method.indexOf("shouldSkipDispatchBySharedDedup")
        val markHandledIndex = method.indexOf("markDispatchHandled(intent, action)")

        assertTrue(contextGuardIndex >= 0, "dispatch hook must guard missing runtime contexts")
        assertTrue(runtimeRecoveryIndex >= 0, "dispatch hook must recover runtime context before parsing")
        assertTrue(sharedDedupIndex >= 0, "dispatch hook must preserve shared-store deduplication")
        assertTrue(markHandledIndex >= 0, "dispatch hook must mark handled before parsing")
        assertTrue(
            runtimeRecoveryIndex < contextGuardIndex,
            "dispatch hook must try fallback context recovery before declaring runtime contexts missing",
        )
        assertTrue(
            contextGuardIndex < markHandledIndex,
            "missing runtime contexts must not consume the intent-extra dedup mark",
        )
        assertTrue(
            sharedDedupIndex < markHandledIndex,
            "shared-store duplicates should be skipped before mutating intent extras",
        )
    }

    @Test
    fun `dispatch context fallback recovers from inbound handler before scanning args`() {
        val source = resolveProjectFile(
            "hook/src/main/java/com/github/magisk317/smscode/xp/hook/code/SmsHandlerHook.kt",
        ).readText()

        val method = source.substringAfter("private fun resolveDispatchPhoneContext")
            .substringBefore("private fun resolveContextFromObject")

        val handlerIndex = method.indexOf("resolveContextFromObject(param.thisObject, \"handler\")")
        val receiverIndex = method.indexOf("resolveContextFromObject(param.args.getOrNull(receiverIndex), \"receiver\")")
        val argsIndex = method.indexOf("param.args.forEachIndexed")

        assertTrue(handlerIndex >= 0, "fallback must inspect the InboundSmsHandler instance")
        assertTrue(receiverIndex >= 0, "fallback should inspect the receiver argument when available")
        assertTrue(argsIndex >= 0, "fallback should scan remaining arguments as a last resort")
        assertTrue(
            handlerIndex < argsIndex,
            "InboundSmsHandler instance is the most stable source of the phone Context",
        )
    }

    @Test
    fun `dispatch context fallback logs recovery stages`() {
        val source = resolveProjectFile(
            "hook/src/main/java/com/github/magisk317/smscode/xp/hook/code/SmsHandlerHook.kt",
        ).readText()

        val method = source.substringAfter("private fun ensureRuntimeForDispatch")
            .substringBefore("private fun resolveDispatchPhoneContext")

        assertTrue(
            "dispatch runtime missing, attempt recovery" in method,
            "missing runtime should be visible in LSPosed/runtime logs",
        )
        assertTrue(
            "dispatch runtime recovery skipped: no phone context" in method,
            "fallback failure must log that no phone context was found",
        )
        assertTrue(
            "dispatch runtime recovery context: source=%s" in method,
            "fallback success must log the source used to recover Context",
        )
        assertTrue(
            "dispatch runtime recovered: source=%s" in method,
            "initialized fallback runtime must be logged before parsing continues",
        )
    }

    @Test
    fun `dispatch chain conflict suppression notifies with event id`() {
        val source = resolveProjectFile(
            "hook/src/main/java/com/github/magisk317/smscode/xp/hook/code/SmsHandlerHook.kt",
        ).readText()

        val method = source.substringAfter("private fun maybeBlockFromDispatchChain")
            .substringBefore("private fun shouldSkipDispatchChainBlock")
        val eventIdIndex = method.indexOf("ensureEventId(intent)")
        val suppressIndex = method.indexOf("ModuleConflictArbiter.shouldSuppressByRelay")
        val notifyIndex = method.indexOf("RelayConflictNoticeHelper.notifyConflictOnSms")

        assertTrue(eventIdIndex >= 0, "dispatch-chain suppression must have a stable event id")
        assertTrue(suppressIndex >= 0, "dispatch-chain conflict suppression guard must remain")
        assertTrue(notifyIndex >= 0, "dispatch-chain conflict suppression must notify the user")
        assertTrue(
            eventIdIndex < suppressIndex,
            "event id must be created before conflict suppression so notification dedupe works",
        )
        assertTrue(
            suppressIndex < notifyIndex,
            "conflict notification must be sent from the suppression branch",
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

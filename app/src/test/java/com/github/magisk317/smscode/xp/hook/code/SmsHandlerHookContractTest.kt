package com.github.magisk317.smscode.xp.hook.code

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SmsHandlerHookContractTest {

    @Test
    fun `dispatch intent marks handled only after runtime contexts are available`() {
        val source = resolveProjectFile(
            "app/src/main/java/com/github/magisk317/smscode/xp/hook/code/SmsHandlerHook.kt",
        ).readText()

        val method = source.substringAfter("private fun beforeDispatchIntentHandler")
            .substringBefore("private fun scheduleBlacklistDelete")
        val contextGuardIndex = method.indexOf("if (pluginContext == null || phoneContext == null)")
        val sharedDedupIndex = method.indexOf("shouldSkipDispatchBySharedDedup")
        val markHandledIndex = method.indexOf("markDispatchHandled(intent, action)")

        assertTrue(contextGuardIndex >= 0, "dispatch hook must guard missing runtime contexts")
        assertTrue(sharedDedupIndex >= 0, "dispatch hook must preserve shared-store deduplication")
        assertTrue(markHandledIndex >= 0, "dispatch hook must mark handled before parsing")
        assertTrue(
            contextGuardIndex < markHandledIndex,
            "missing runtime contexts must not consume the intent-extra dedup mark",
        )
        assertTrue(
            sharedDedupIndex < markHandledIndex,
            "shared-store duplicates should be skipped before mutating intent extras",
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

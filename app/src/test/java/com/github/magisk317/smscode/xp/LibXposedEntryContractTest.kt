package com.github.magisk317.smscode.xp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LibXposedEntryContractTest {

    @Test
    fun `libxposed entrypoint and hot reload metadata stay aligned`() {
        assertEquals(
            "com.github.magisk317.smscode.xp.LibXposedEntry",
            resolveProjectFile("app/src/main/resources/META-INF/xposed/java_init.list").readText().trim(),
        )

        val moduleProps = resolveProjectFile("app/src/main/resources/META-INF/xposed/module.prop").readText()
        assertTrue("minApiVersion=102" in moduleProps)
        assertTrue("targetApiVersion=102" in moduleProps)
        assertTrue("staticScope=true" in moduleProps)
        assertTrue("autoHotReload=true" in moduleProps)

        val scope = resolveProjectFile("app/src/main/resources/META-INF/xposed/scope.list")
            .readLines()
            .filter { it.isNotBlank() }
            .toSet()
        assertTrue("system" in scope)
        assertTrue("android" in scope)
        assertTrue("com.android.phone" in scope)
        assertTrue("com.xiaomi.phone" in scope)
        assertTrue("com.android.providers.telephony" in scope)
        assertTrue("com.android.mms" in scope)
    }

    @Test
    fun `hot reload stores only parcelable process and package state`() {
        val entrySource = resolveProjectFile(
            "app/src/main/java/com/github/magisk317/smscode/xp/LibXposedEntry.kt",
        ).readText()

        assertTrue("HotReloadingParam" in entrySource)
        assertTrue("HotReloadedParam" in entrySource)
        assertTrue("param.setSavedInstanceState(createHotReloadState())" in entrySource)
        assertTrue("putString(STATE_PROCESS_NAME" in entrySource)
        assertTrue("putStringArrayList(STATE_LOADED_PACKAGES" in entrySource)
        assertTrue("hookApi.beginHotReload(param.oldHookHandles)" in entrySource)
        assertTrue("hookApi.finishHotReload()" in entrySource)
        assertFalse("setSavedInstanceState(Pair(" in entrySource)
        assertFalse("HashMap(loadedPackages)" in entrySource)
        assertFalse("savedInstanceState as? Pair" in entrySource)
        assertFalse("ClassLoader)" in entrySource.substringAfter("fun createHotReloadState"))
    }

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val parent = File("../$relativePath")
        require(parent.isFile) { "Cannot resolve file: $relativePath" }
        return parent
    }
}

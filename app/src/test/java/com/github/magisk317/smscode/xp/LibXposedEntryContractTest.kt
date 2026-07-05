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
        assertTrue("com.google.android.apps.messaging" in scope)
    }

    @Test
    fun `hot reload stores only parcelable process and package state`() {
        val appEntrySource = resolveProjectFile(
            "app/src/main/java/com/github/magisk317/smscode/xp/LibXposedEntry.kt",
        ).readText()
        val baseEntrySource = resolveProjectFile(
            "magisk-xposed-kit/src/main/java/io/github/magisk317/xposed/BaseLibXposedEntry.kt",
        ).readText()

        assertTrue("HotReloadingParam" in baseEntrySource)
        assertTrue("HotReloadedParam" in baseEntrySource)
        assertTrue("com.google.android.apps.messaging" in baseEntrySource)
        assertTrue("param.setSavedInstanceState(createHotReloadState())" in baseEntrySource)
        assertTrue("dispatchCurrentLoadedTargets(param, phase = \"moduleLoadedCurrentProcess\")" in baseEntrySource)
        assertTrue("resolveCurrentProcessTargets(param, oldHookHandles)" in baseEntrySource)
        assertTrue("phase = \"hotReload\"" in baseEntrySource)
        assertTrue("fun resolveCurrentLoadedTargets(param: ModuleLoadedParam)" in baseEntrySource)
        assertTrue("putString(STATE_PROCESS_NAME" in baseEntrySource)
        assertTrue("putStringArrayList(STATE_LOADED_PACKAGES" in baseEntrySource)
        assertTrue("hookApi.beginHotReload(oldHookHandles)" in baseEntrySource)
        assertTrue("hookApi.finishHotReload()" in baseEntrySource)
        assertFalse("setSavedInstanceState(Pair(" in baseEntrySource)
        assertFalse("HashMap(loadedPackages)" in baseEntrySource)
        assertFalse("savedInstanceState as? Pair" in baseEntrySource)
        val hotReloadFun = baseEntrySource.substringAfter("fun createHotReloadState()").substringBefore("\n    private fun ")
        assertFalse("ClassLoader)" in hotReloadFun)
    }

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val parent = File("../$relativePath")
        require(parent.isFile) { "Cannot resolve file: $relativePath" }
        return parent
    }
}

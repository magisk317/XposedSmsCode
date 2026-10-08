package io.github.magisk317.smscode.xp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LibXposedEntryContractTest {

    @Test
    fun `libxposed entrypoint and hot reload metadata stay aligned`() {
        assertEquals(
            "io.github.magisk317.smscode.xp.LibXposedEntry",
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
    fun `sms code entry does not install relay notification ingress`() {
        val entrySource = resolveProjectFile(
            "hook/src/main/kotlin/io/github/magisk317/smscode/xp/LibXposedEntry.kt",
        ).readText()
        val manifest = resolveProjectFile("app/src/main/AndroidManifest.xml").readText()

        assertFalse("NotificationManagerHook()" in entrySource)
        assertFalse("ForwardReceiver" in manifest)
        assertFalse("ACTION_FORWARD_SMS" in manifest)
    }

    @Test
    fun `release shrinking preserves libxposed entry and hook contracts`() {
        val rules = resolveProjectFile("app/proguard-common.pro").readText()

        assertTrue("-keep class io.github.magisk317.smscode.xp.** { *; }" in rules)
        assertTrue("-keep class io.github.magisk317.xposed.** { *; }" in rules)
        assertTrue("implements io.github.libxposed.api.XposedInterface\$Hooker" in rules)
        assertTrue("extends io.github.libxposed.api.XposedModule" in rules)
        assertTrue("-dontwarn io.github.libxposed.api.**" in rules)
    }

    @Test
    fun `hot reload stores only parcelable process and package state`() {
        val appEntrySource = resolveProjectFile(
            "hook/src/main/kotlin/io/github/magisk317/smscode/xp/LibXposedEntry.kt",
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

package io.github.magisk317.smscode.xp.hook.me

import io.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.xposed.capability.me.ModuleUtilsHook as SharedModuleUtilsHook

class ModuleUtilsHook : SharedModuleUtilsHook(
    targetPackage = BuildConfig.APPLICATION_ID,
    moduleVersion = BuildConfig.MODULE_VERSION,
)

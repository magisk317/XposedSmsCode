package com.github.magisk317.smscode.xp.hook.me

import com.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.smscode.xposed.hook.me.ModuleUtilsHook as SharedModuleUtilsHook

class ModuleUtilsHook : SharedModuleUtilsHook(
    targetPackage = BuildConfig.APPLICATION_ID,
    moduleVersion = BuildConfig.MODULE_VERSION,
)

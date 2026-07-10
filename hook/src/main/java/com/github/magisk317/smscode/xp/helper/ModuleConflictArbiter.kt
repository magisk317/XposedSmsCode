package com.github.magisk317.smscode.xp.helper

import android.content.Context
import com.github.magisk317.smscode.hook.BuildConfig
import com.github.magisk317.smscode.common.constant.TransitionConst
import io.github.magisk317.smscode.verification.ModuleConflictArbiterCore

object ModuleConflictArbiter {
    val SUPPRESSION_REASON: String = ModuleConflictArbiterCore.SUPPRESSION_REASON
    val BYPASS_REASON_BUILD_FLAG: String = ModuleConflictArbiterCore.BYPASS_REASON_BUILD_FLAG
    const val TARGET_RELAY_PACKAGE = TransitionConst.TARGET_RELAY_PACKAGE

    private val arbiter = ModuleConflictArbiterCore(
        targetPackage = TARGET_RELAY_PACKAGE,
        allowConflictBypass = { BuildConfig.ALLOW_CONFLICT_BYPASS },
    )

    fun shouldSuppressByRelay(context: Context?, source: String): Boolean {
        return arbiter.shouldSuppress(context, source)
    }
}

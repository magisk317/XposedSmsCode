package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import io.github.magisk317.smscode.verification.VerificationRuntimeContext

internal data class SmsHookRuntimeContext(
    override val phoneContext: Context,
    override val pluginContext: Context,
) : VerificationRuntimeContext

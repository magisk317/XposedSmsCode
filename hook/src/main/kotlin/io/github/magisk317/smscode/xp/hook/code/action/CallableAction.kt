package io.github.magisk317.smscode.xp.hook.code.action

import android.content.Context
import android.os.Bundle
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.verification.SmsCodeCallableAction

/**
 * Action + Callable
 */
abstract class CallableAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
) : SmsCodeCallableAction<SmsMsg>(pluginContext, phoneContext, smsMsg),
    Action<Bundle?>

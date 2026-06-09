package com.github.magisk317.smscode.xp.hook.code.action

import android.content.Context
import android.os.Bundle
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.SmsCodeCallableAction

/**
 * Action + Callable
 */
abstract class CallableAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
) : SmsCodeCallableAction<SmsMsg>(pluginContext, phoneContext, smsMsg),
    Action<Bundle?>

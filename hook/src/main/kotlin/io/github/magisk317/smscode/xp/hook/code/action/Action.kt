package io.github.magisk317.smscode.xp.hook.code.action

import io.github.magisk317.smscode.runtime.verification.SmsCodeAction

/** Alias of the shared [SmsCodeAction] contract; the twin file was retired when
 *  both apps moved onto the kit action framework. */
typealias Action<T> = SmsCodeAction<T>

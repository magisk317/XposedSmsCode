package com.tianma.xsmscode.xp.hook.code.helper

import android.annotation.SuppressLint
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import de.robv.android.xposed.XposedHelpers

/**
 * Helper for InputMethod Input Characters.<br/>
 * Refer: com.android.commands.input.Input
 */
object InputHelper {

    /**
     * refer: com.android.commands.input.Input#sendText()
     *
     * @throws Throwable throwable throws if the caller has no android.permission.INJECT_EVENTS permission
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun sendText(text: String?) {
        if (text == null) return
        val source = InputDevice.SOURCE_KEYBOARD
        val sb = StringBuilder(text)

        var escapeFlag = false
        var i = 0
        while (i < sb.length) {
            if (escapeFlag) {
                escapeFlag = false
                if (sb[i] == 's') {
                    sb.setCharAt(i, ' ')
                    sb.deleteCharAt(--i)
                }
            }
            if (i >= 0 && i < sb.length && sb[i] == '%') {
                escapeFlag = true
            }
            i++
        }

        val chars = sb.toString().toCharArray()
        val kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = kcm.getEvents(chars) ?: return
        for (keyEvent in events) {
            if (source != keyEvent.source) {
                keyEvent.source = source
            }
            injectKeyEvent(keyEvent)
        }
    }

    @JvmStatic
    @Throws(Throwable::class)
    fun sendKeyEvent(inputSource: Int, keyCode: Int, longpress: Boolean) {
        val now = SystemClock.uptimeMillis()
        injectKeyEvent(
            KeyEvent(
                now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, inputSource
            )
        )
        if (longpress) {
            injectKeyEvent(
                KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, keyCode, 1, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_LONG_PRESS,
                    inputSource
                )
            )
        }
        injectKeyEvent(
            KeyEvent(
                now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, inputSource
            )
        )
    }

    /**
     * refer com.android.commands.input.Input#injectKeyEvent()
     */
    @SuppressLint("PrivateApi")
    @Throws(Throwable::class)
    private fun injectKeyEvent(keyEvent: KeyEvent) {
        val inputManager = XposedHelpers.callStaticMethod(InputManager::class.java, "getInstance")

        val injectInputEventModeWaitForFinish = XposedHelpers.getStaticIntField(
            InputManager::class.java, "INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH"
        ) as Int

        val paramTypes = arrayOf(KeyEvent::class.java, Int::class.javaPrimitiveType)
        val args = arrayOf(keyEvent, injectInputEventModeWaitForFinish)

        XposedHelpers.callMethod(inputManager, "injectInputEvent", paramTypes, args)
    }
}

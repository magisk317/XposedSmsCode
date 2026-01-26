package com.tianma.xsmscode.data.eventbus

import org.greenrobot.eventbus.EventBus

/**
 * Event bus utils
 */
object XEventBus {
    private fun get(): EventBus {
        return EventBus.getDefault()
    }

    @JvmStatic
    fun post(event: Any) {
        get().post(event)
    }

    @JvmStatic
    fun register(subscriber: Any) {
        get().register(subscriber)
    }

    @JvmStatic
    fun unregister(subscriber: Any) {
        get().unregister(subscriber)
    }
}

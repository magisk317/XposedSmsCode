package com.github.magisk317.smscode.common.constant

object CodeNotificationOwner {
    const val APP = "app"
    const val PHONE = "phone"
    const val DEFAULT = PHONE

    fun normalize(value: String?): String {
        return when (value) {
            APP -> APP
            PHONE -> PHONE
            else -> DEFAULT
        }
    }
}

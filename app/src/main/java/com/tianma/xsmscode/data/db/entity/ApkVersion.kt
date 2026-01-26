package com.tianma.xsmscode.data.db.entity

import kotlin.math.max

/**
 * Apk version info
 */
class ApkVersion(val versionName: String, val versionInfo: String?) : Comparable<ApkVersion> {

    override fun toString(): String {
        return "ApkVersion{mVersionName='$versionName', mVersionInfo='$versionInfo'}"
    }

    override fun compareTo(other: ApkVersion): Int {
        val thisParts = this.versionName.split("\\.".toRegex()).toTypedArray()
        val thatParts = other.versionName.split("\\.".toRegex()).toTypedArray()
        val maxLength = max(thisParts.size, thatParts.size)
        for (i in 0 until maxLength) {
            val thisPart = if (i < thisParts.size) thisParts[i].toInt() else 0
            val thatPart = if (i < thatParts.size) thatParts[i].toInt() else 0
            if (thisPart < thatPart) {
                return -1
            } else if (thisPart > thatPart) {
                return 1
            }
        }
        return 0
    }
}

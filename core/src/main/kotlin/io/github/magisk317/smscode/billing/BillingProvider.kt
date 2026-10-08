package io.github.magisk317.smscode.billing

import android.app.Activity

/** Minimal interface for Play donations. Real implementation lives in the
 *  play flavor; github/non-play gets a no-op stub. There is deliberately no
 *  subscription tier in any consumer app. */
interface BillingProvider {
    fun isAvailable(): Boolean
    fun launchDonation(activity: Activity, productId: String)
}

class NoOpBillingProvider : BillingProvider {
    override fun isAvailable(): Boolean = false
    override fun launchDonation(activity: Activity, productId: String) {}
}

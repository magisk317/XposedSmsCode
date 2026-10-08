package io.github.magisk317.smscode.billing

import android.app.Activity
import io.github.magisk317.uikit.billing.BillingManager

class PlayBillingProvider(
    private val billingManager: BillingManager,
) : BillingProvider {

    override fun isAvailable(): Boolean = billingManager.isReady()

    override fun launchDonation(activity: Activity, productId: String) {
        billingManager.launchDonationById(activity, productId)
    }
}

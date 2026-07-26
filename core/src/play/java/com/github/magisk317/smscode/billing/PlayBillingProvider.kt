package com.github.magisk317.smscode.billing

import android.app.Activity
import io.github.magisk317.uikit.billing.BillingManager

class PlayBillingProvider(
    private val billingManager: BillingManager,
) : BillingProvider {

    override fun isAvailable(): Boolean = billingManager.isReady()

    override fun isSubscriptionActive(): Boolean = false

    override fun getActiveProductId(): String? = null

    override fun refreshSubscriptionStatus() {}

    override fun launchSubscription(activity: Activity, productId: String) {
        val details = billingManager.subscriptionDetails.value.find { detail ->
            detail.productId == productId
        } ?: return
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        billingManager.launchPurchaseFlow(activity, details, offerToken)
    }

    override fun launchDonation(activity: Activity, productId: String) {
        val details = billingManager.donationDetails.value.find { detail ->
            detail.productId == productId
        } ?: return
        billingManager.launchDonationFlow(activity, details)
    }
}

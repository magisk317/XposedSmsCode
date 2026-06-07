package com.github.magisk317.smscode.billing

import android.app.Activity

interface BillingProvider {
    fun isAvailable(): Boolean
    fun isSubscriptionActive(): Boolean
    fun getActiveProductId(): String?
    fun refreshSubscriptionStatus()
    fun launchSubscription(activity: Activity, productId: String)
    fun launchDonation(activity: Activity, productId: String)
}

class NoOpBillingProvider : BillingProvider {
    override fun isAvailable(): Boolean = false
    override fun isSubscriptionActive(): Boolean = false
    override fun getActiveProductId(): String? = null
    override fun refreshSubscriptionStatus() {}
    override fun launchSubscription(activity: Activity, productId: String) {}
    override fun launchDonation(activity: Activity, productId: String) {}
}

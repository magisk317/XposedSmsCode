package com.github.magisk317.smscode.billing

object ProductConfig {
    // Subscription product IDs (must be created in Google Play Console)
    val SUBSCRIPTION_IDS = listOf(
        "sub_monthly",  // $0.99/month
        "sub_yearly",   // $9.99/year
    )

    // In-app product IDs for donations
    val DONATION_IDS = listOf(
        "donate_099",   // $0.99
        "donate_200",   // $2.00
        "donate_999",   // $9.99
        "donate_1999",  // $19.99
    )

    // Donations are intentionally repeatable. Never infer consumption from INAPP alone: any
    // future entitlement remains non-consumable until it is explicitly added here.
    private val CONSUMABLE_PRODUCT_IDS = DONATION_IDS.toSet()

    internal val PURCHASE_POLICY = BillingProductPolicy(
        subscriptionProductIds = SUBSCRIPTION_IDS.toSet(),
        consumableProductIds = CONSUMABLE_PRODUCT_IDS,
    )
}

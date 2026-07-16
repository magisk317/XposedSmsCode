package com.github.magisk317.smscode.billing

import android.app.Application
import com.github.magisk317.smscode.app.AppInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BillingInitializer(
    private val billingManager: BillingManager,
) : AppInitializer {

    override fun init(application: Application) {
        billingManager.startConnection {
            // Connected to Google Play Billing
            CoroutineScope(Dispatchers.IO).launch {
                billingManager.querySubscriptions()
                billingManager.queryDonations()
                billingManager.queryActivePurchases()
            }
        }
    }
}

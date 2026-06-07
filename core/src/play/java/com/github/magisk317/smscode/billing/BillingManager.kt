package com.github.magisk317.smscode.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

class BillingManager(context: Context) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    private val _subscriptionDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val subscriptionDetails: StateFlow<List<ProductDetails>> = _subscriptionDetails.asStateFlow()

    private val _donationDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val donationDetails: StateFlow<List<ProductDetails>> = _donationDetails.asStateFlow()

    private val _activePurchases = MutableStateFlow<List<Purchase>>(emptyList())
    val activePurchases: StateFlow<List<Purchase>> = _activePurchases.asStateFlow()



    private var purchaseCallback: ((BillingResult, List<Purchase>?) -> Unit)? = null

    fun startConnection(onReady: (() -> Unit)? = null) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Timber.d("Billing connected")
                    onReady?.invoke()
                } else {
                    Timber.w("Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Timber.w("Billing disconnected")
            }
        })
    }

    suspend fun querySubscriptions(): List<ProductDetails> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ProductConfig.SUBSCRIPTION_IDS.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()

        return suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { billingResult: BillingResult, result: QueryProductDetailsResult ->
                val details = result.productDetailsList
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _subscriptionDetails.value = details
                    cont.resume(details)
                } else {
                    Timber.w("Query subscriptions failed: ${billingResult.debugMessage}")
                    cont.resume(emptyList())
                }
            }
        }
    }

    suspend fun queryDonations(): List<ProductDetails> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ProductConfig.DONATION_IDS.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()

        return suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { billingResult: BillingResult, result: QueryProductDetailsResult ->
                val details = result.productDetailsList
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _donationDetails.value = details
                    cont.resume(details)
                } else {
                    Timber.w("Query donations failed: ${billingResult.debugMessage}")
                    cont.resume(emptyList())
                }
            }
        }
    }

    suspend fun queryActivePurchases(): List<Purchase> {
        return suspendCancellableCoroutine { cont ->
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
            ) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _activePurchases.value = purchases
                    cont.resume(purchases)
                } else {
                    cont.resume(emptyList())
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails, offerToken: String? = null) {
        val flowParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (offerToken != null) {
            flowParamsBuilder.setOfferToken(offerToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(flowParamsBuilder.build()))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    fun launchDonationFlow(activity: Activity, productDetails: ProductDetails) {
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build(),
                ),
            )
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        purchaseCallback?.invoke(result, purchases)
        purchaseCallback = null

        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            _activePurchases.value = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        }
    }

    fun setPurchaseCallback(callback: (BillingResult, List<Purchase>?) -> Unit) {
        purchaseCallback = callback
    }

    fun isReady(): Boolean = billingClient.isReady

    fun endConnection() {
        billingClient.endConnection()
    }
}

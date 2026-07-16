package com.github.magisk317.smscode.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

class BillingManager internal constructor(
    clientFactory: BillingClientGatewayFactory,
    private val productPolicy: BillingProductPolicy,
    private val reconnectScheduler: BillingReconnectScheduler,
    private val retryDelayMillis: (Int) -> Long = ::defaultRetryDelayMillis,
) : PurchasesUpdatedListener {

    constructor(context: Context) : this(
        clientFactory = GooglePlayBillingClientGateway.factory(context.applicationContext),
        productPolicy = ProductConfig.PURCHASE_POLICY,
        reconnectScheduler = CoroutineBillingReconnectScheduler(),
    )

    private val billingClient = clientFactory.create(this)
    private val connectionLock = Any()
    private val readyCallbacks = LinkedHashSet<() -> Unit>()
    private var connectionState = ConnectionState.DISCONNECTED
    private var reconnectScheduled = false
    private var reconnectAttempt = 0

    private val purchaseStateLock = Any()
    private val purchasesByType = mutableMapOf(
        BillingClient.ProductType.SUBS to emptyList<Purchase>(),
        BillingClient.ProductType.INAPP to emptyList(),
    )
    private val settlementsInFlight = mutableSetOf<String>()
    private val successfullySettledTokens = mutableSetOf<String>()

    private val _subscriptionDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val subscriptionDetails: StateFlow<List<ProductDetails>> = _subscriptionDetails.asStateFlow()

    private val _donationDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val donationDetails: StateFlow<List<ProductDetails>> = _donationDetails.asStateFlow()

    private val _activePurchases = MutableStateFlow<List<Purchase>>(emptyList())
    val activePurchases: StateFlow<List<Purchase>> = _activePurchases.asStateFlow()

    private var purchaseCallback: ((BillingResult, List<Purchase>?) -> Unit)? = null

    private val connectionListener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(result: BillingResult) {
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val callbacks = synchronized(connectionLock) {
                    connectionState = ConnectionState.READY
                    reconnectScheduled = false
                    reconnectAttempt = 0
                    readyCallbacks.toList()
                }
                Timber.d("Billing connected")
                callbacks.forEach(::invokeReadyCallback)
            } else {
                Timber.w("Billing setup failed: ${result.debugMessage}")
                markDisconnectedAndRetry()
            }
        }

        override fun onBillingServiceDisconnected() {
            Timber.w("Billing disconnected")
            markDisconnectedAndRetry()
        }
    }

    fun startConnection(onReady: (() -> Unit)? = null) {
        val invokeImmediately = synchronized(connectionLock) {
            if (connectionState == ConnectionState.CLOSED) {
                return
            }
            if (onReady != null) {
                readyCallbacks += onReady
            }
            onReady != null && (connectionState == ConnectionState.READY || billingClient.isReady)
        }

        if (invokeImmediately) {
            invokeReadyCallback(requireNotNull(onReady))
        } else {
            connectIfNeeded()
        }
    }

    suspend fun querySubscriptions(): List<ProductDetails> = queryProductDetails(
        productIds = ProductConfig.SUBSCRIPTION_IDS,
        productType = BillingClient.ProductType.SUBS,
        currentValue = subscriptionDetails.value,
        updateValue = { _subscriptionDetails.value = it },
    )

    suspend fun queryDonations(): List<ProductDetails> = queryProductDetails(
        productIds = ProductConfig.DONATION_IDS,
        productType = BillingClient.ProductType.INAPP,
        currentValue = donationDetails.value,
        updateValue = { _donationDetails.value = it },
    )

    suspend fun queryActivePurchases(): List<Purchase> {
        if (!ensureReady()) {
            return activePurchases.value
        }

        queryPurchases(BillingClient.ProductType.SUBS)?.let { purchases ->
            replacePurchases(BillingClient.ProductType.SUBS, purchases)
            settlePurchases(purchases)
        }
        queryPurchases(BillingClient.ProductType.INAPP)?.let { purchases ->
            replacePurchases(BillingClient.ProductType.INAPP, purchases)
            settlePurchases(purchases)
        }
        return activePurchases.value
    }

    fun launchPurchaseFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String? = null,
    ): BillingResult {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .also { builder ->
                if (offerToken != null) {
                    builder.setOfferToken(offerToken)
                }
            }
            .build()
        return launchBillingFlow(activity, productParams)
    }

    fun launchDonationFlow(activity: Activity, productDetails: ProductDetails): BillingResult {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()
        return launchBillingFlow(activity, productParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            mergePurchaseUpdates(purchases)
        } else {
            handleOperationFailure(result)
        }

        val callback = synchronized(connectionLock) {
            purchaseCallback.also { purchaseCallback = null }
        }
        if (callback != null) {
            runCatching { callback(result, purchases) }
                .onFailure { error -> Timber.e(error, "Purchase callback failed") }
        }

        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            settlePurchases(purchases)
        }
    }

    fun setPurchaseCallback(callback: (BillingResult, List<Purchase>?) -> Unit) {
        synchronized(connectionLock) {
            purchaseCallback = callback
        }
    }

    fun isReady(): Boolean = billingClient.isReady

    fun endConnection() {
        val shouldClose = synchronized(connectionLock) {
            if (connectionState == ConnectionState.CLOSED) {
                false
            } else {
                connectionState = ConnectionState.CLOSED
                reconnectScheduled = false
                readyCallbacks.clear()
                purchaseCallback = null
                true
            }
        }
        if (shouldClose) {
            reconnectScheduler.cancel()
            billingClient.endConnection()
        }
    }

    private fun connectIfNeeded() {
        val shouldConnect = synchronized(connectionLock) {
            when {
                connectionState == ConnectionState.CLOSED -> false
                billingClient.isReady -> {
                    connectionState = ConnectionState.READY
                    false
                }
                connectionState == ConnectionState.CONNECTING -> false
                else -> {
                    connectionState = ConnectionState.CONNECTING
                    true
                }
            }
        }
        if (!shouldConnect) {
            return
        }

        runCatching { billingClient.startConnection(connectionListener) }
            .onFailure { error ->
                Timber.w(error, "Unable to start Billing connection")
                markDisconnectedAndRetry()
            }
    }

    private fun markDisconnectedAndRetry() {
        val retryDelay = synchronized(connectionLock) {
            if (connectionState == ConnectionState.CLOSED || reconnectScheduled) {
                return
            }
            connectionState = ConnectionState.DISCONNECTED
            reconnectScheduled = true
            retryDelayMillis(reconnectAttempt++)
        }
        reconnectScheduler.schedule(retryDelay) {
            synchronized(connectionLock) {
                reconnectScheduled = false
            }
            connectIfNeeded()
        }
    }

    private fun ensureReady(): Boolean {
        if (billingClient.isReady) {
            synchronized(connectionLock) {
                if (connectionState != ConnectionState.CLOSED) {
                    connectionState = ConnectionState.READY
                }
            }
            return true
        }
        connectIfNeeded()
        return false
    }

    private suspend fun queryProductDetails(
        productIds: List<String>,
        productType: String,
        currentValue: List<ProductDetails>,
        updateValue: (List<ProductDetails>) -> Unit,
    ): List<ProductDetails> {
        if (!ensureReady()) {
            return currentValue
        }
        return suspendCancellableCoroutine { continuation ->
            billingClient.queryProductDetails(productIds, productType) { result, details ->
                val value = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    updateValue(details)
                    details
                } else {
                    Timber.w("Query product details failed: ${result.debugMessage}")
                    handleOperationFailure(result)
                    currentValue
                }
                if (continuation.isActive) {
                    continuation.resume(value)
                }
            }
        }
    }

    private suspend fun queryPurchases(productType: String): List<Purchase>? =
        suspendCancellableCoroutine { continuation ->
            billingClient.queryPurchases(productType) { result, purchases ->
                val value = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases
                } else {
                    Timber.w("Query purchases failed: ${result.debugMessage}")
                    handleOperationFailure(result)
                    null
                }
                if (continuation.isActive) {
                    continuation.resume(value)
                }
            }
        }

    private fun launchBillingFlow(
        activity: Activity,
        productParams: BillingFlowParams.ProductDetailsParams,
    ): BillingResult {
        val result = if (ensureReady()) {
            billingClient.launchBillingFlow(activity, productParams)
        } else {
            billingResult(
                responseCode = BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                debugMessage = "Billing service is reconnecting",
            )
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onPurchasesUpdated(result, null)
        }
        return result
    }

    private fun replacePurchases(productType: String, purchases: List<Purchase>) {
        synchronized(purchaseStateLock) {
            purchasesByType[productType] = purchases.activeOnly()
            publishPurchasesLocked()
        }
    }

    private fun mergePurchaseUpdates(purchases: List<Purchase>) {
        synchronized(purchaseStateLock) {
            purchases.activeOnly().groupBy(productPolicy::productTypeFor).forEach { (productType, updates) ->
                val merged = purchasesByType.getValue(productType)
                    .associateByTo(LinkedHashMap(), Purchase::getPurchaseToken)
                updates.forEach { purchase -> merged[purchase.purchaseToken] = purchase }
                purchasesByType[productType] = merged.values.toList()
            }
            publishPurchasesLocked()
        }
    }

    private fun publishPurchasesLocked() {
        _activePurchases.value = purchasesByType.values
            .flatten()
            .distinctBy(Purchase::getPurchaseToken)
            .sortedBy(Purchase::getPurchaseToken)
    }

    private fun settlePurchases(purchases: List<Purchase>) {
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .forEach(::settlePurchase)
    }

    private fun settlePurchase(purchase: Purchase) {
        val token = purchase.purchaseToken
        val settlement = productPolicy.settlementFor(purchase)
        if (token.isBlank() || (settlement == PurchaseSettlement.ACKNOWLEDGE && purchase.isAcknowledged)) {
            return
        }
        val shouldSettle = synchronized(purchaseStateLock) {
            token !in settlementsInFlight && token !in successfullySettledTokens &&
                settlementsInFlight.add(token)
        }
        if (!shouldSettle) {
            return
        }

        val completion: (BillingResult) -> Unit = { result ->
            synchronized(purchaseStateLock) {
                settlementsInFlight -= token
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    successfullySettledTokens += token
                    if (settlement == PurchaseSettlement.CONSUME) {
                        purchasesByType.keys.forEach { productType ->
                            purchasesByType[productType] = purchasesByType.getValue(productType)
                                .filterNot { it.purchaseToken == token }
                        }
                        publishPurchasesLocked()
                    }
                }
            }
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Timber.w("Purchase settlement failed: ${result.debugMessage}")
                handleOperationFailure(result)
            }
        }

        when (settlement) {
            PurchaseSettlement.ACKNOWLEDGE -> billingClient.acknowledgePurchase(token, completion)
            PurchaseSettlement.CONSUME -> billingClient.consumePurchase(token, completion)
        }
    }

    private fun handleOperationFailure(result: BillingResult) {
        if (
            result.responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ||
            result.responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
        ) {
            markDisconnectedAndRetry()
        }
    }

    private fun invokeReadyCallback(callback: () -> Unit) {
        runCatching(callback)
            .onFailure { error -> Timber.e(error, "Billing ready callback failed") }
    }

    private enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        READY,
        CLOSED,
    }
}

internal enum class PurchaseSettlement {
    ACKNOWLEDGE,
    CONSUME,
}

internal class BillingProductPolicy(
    private val subscriptionProductIds: Set<String>,
    private val consumableProductIds: Set<String>,
) {
    fun settlementFor(purchase: Purchase): PurchaseSettlement =
        if (purchase.products.isNotEmpty() && purchase.products.all(consumableProductIds::contains)) {
            PurchaseSettlement.CONSUME
        } else {
            PurchaseSettlement.ACKNOWLEDGE
        }

    fun productTypeFor(purchase: Purchase): String =
        if (purchase.products.any(subscriptionProductIds::contains)) {
            BillingClient.ProductType.SUBS
        } else {
            BillingClient.ProductType.INAPP
        }
}

internal fun interface BillingClientGatewayFactory {
    fun create(listener: PurchasesUpdatedListener): BillingClientGateway
}

internal interface BillingClientGateway {
    val isReady: Boolean

    fun startConnection(listener: BillingClientStateListener)

    fun queryProductDetails(
        productIds: List<String>,
        productType: String,
        callback: (BillingResult, List<ProductDetails>) -> Unit,
    )

    fun queryPurchases(
        productType: String,
        callback: (BillingResult, List<Purchase>) -> Unit,
    )

    fun acknowledgePurchase(purchaseToken: String, callback: (BillingResult) -> Unit)

    fun consumePurchase(purchaseToken: String, callback: (BillingResult) -> Unit)

    fun launchBillingFlow(
        activity: Activity,
        productParams: BillingFlowParams.ProductDetailsParams,
    ): BillingResult

    fun endConnection()
}

private class GooglePlayBillingClientGateway(
    context: Context,
    listener: PurchasesUpdatedListener,
) : BillingClientGateway {
    private val client = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    override val isReady: Boolean
        get() = client.isReady

    override fun startConnection(listener: BillingClientStateListener) {
        client.startConnection(listener)
    }

    override fun queryProductDetails(
        productIds: List<String>,
        productType: String,
        callback: (BillingResult, List<ProductDetails>) -> Unit,
    ) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(productType)
                        .build()
                },
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            callback(result, details.productDetailsList)
        }
    }

    override fun queryPurchases(
        productType: String,
        callback: (BillingResult, List<Purchase>) -> Unit,
    ) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()
        client.queryPurchasesAsync(params, callback)
    }

    override fun acknowledgePurchase(purchaseToken: String, callback: (BillingResult) -> Unit) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        client.acknowledgePurchase(params, callback)
    }

    override fun consumePurchase(purchaseToken: String, callback: (BillingResult) -> Unit) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        client.consumeAsync(params) { result, _ -> callback(result) }
    }

    override fun launchBillingFlow(
        activity: Activity,
        productParams: BillingFlowParams.ProductDetailsParams,
    ): BillingResult {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        return client.launchBillingFlow(activity, params)
    }

    override fun endConnection() {
        client.endConnection()
    }

    companion object {
        fun factory(context: Context) = BillingClientGatewayFactory { listener ->
            GooglePlayBillingClientGateway(context, listener)
        }
    }
}

internal interface BillingReconnectScheduler {
    fun schedule(delayMillis: Long, callback: () -> Unit)

    fun cancel()
}

private class CoroutineBillingReconnectScheduler : BillingReconnectScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null

    override fun schedule(delayMillis: Long, callback: () -> Unit) {
        if (reconnectJob?.isActive == true) {
            return
        }
        reconnectJob = scope.launch {
            delay(delayMillis)
            callback()
        }
    }

    override fun cancel() {
        scope.cancel()
    }
}

private fun List<Purchase>.activeOnly(): List<Purchase> = filter { purchase ->
    purchase.purchaseState == Purchase.PurchaseState.PURCHASED ||
        purchase.purchaseState == Purchase.PurchaseState.PENDING
}

private fun billingResult(responseCode: Int, debugMessage: String): BillingResult =
    BillingResult.newBuilder()
        .setResponseCode(responseCode)
        .setDebugMessage(debugMessage)
        .build()

private fun defaultRetryDelayMillis(attempt: Int): Long =
    (INITIAL_RETRY_DELAY_MILLIS shl attempt.coerceAtMost(MAX_RETRY_SHIFT))
        .coerceAtMost(MAX_RETRY_DELAY_MILLIS)

private const val INITIAL_RETRY_DELAY_MILLIS = 500L
private const val MAX_RETRY_DELAY_MILLIS = 30_000L
private const val MAX_RETRY_SHIFT = 6

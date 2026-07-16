package com.github.magisk317.smscode.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BillingManagerTest {

    @Test
    fun `active query covers subscriptions and in-app products with explicit settlement`() = runBlocking {
        val subscription = purchase("sub-token", "sub_monthly")
        val donation = purchase("donation-token", "donate_099")
        val gateway = FakeBillingClientGateway().apply {
            queryResponse(BillingClient.ProductType.SUBS, OK, listOf(subscription))
            queryResponse(BillingClient.ProductType.INAPP, OK, listOf(donation))
        }
        val manager = manager(gateway)

        val active = manager.queryActivePurchases()

        assertEquals(
            listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP),
            gateway.queriedProductTypes,
        )
        assertEquals(listOf("sub-token"), gateway.acknowledgedTokens)
        assertEquals(listOf("donation-token"), gateway.consumedTokens)
        assertEquals(listOf("sub-token"), active.map(Purchase::getPurchaseToken))
    }

    @Test
    fun `failed queries preserve the last successful purchase snapshot`() = runBlocking {
        val subscription = purchase("sub-token", "sub_monthly", acknowledged = true)
        val gateway = FakeBillingClientGateway().apply {
            queryResponse(BillingClient.ProductType.SUBS, OK, listOf(subscription))
            queryResponse(BillingClient.ProductType.INAPP, OK, emptyList())
        }
        val manager = manager(gateway)
        manager.queryActivePurchases()

        gateway.queryResponse(BillingClient.ProductType.SUBS, ERROR, emptyList())
        gateway.queryResponse(BillingClient.ProductType.INAPP, ERROR, emptyList())

        assertEquals(listOf("sub-token"), manager.queryActivePurchases().map(Purchase::getPurchaseToken))
    }

    @Test
    fun `disconnect schedules one reconnect and ready callback runs after recovery`() {
        val gateway = FakeBillingClientGateway().apply { ready = false }
        val scheduler = FakeReconnectScheduler()
        val manager = manager(gateway, scheduler)
        var readyCallbacks = 0

        manager.startConnection { readyCallbacks++ }
        assertEquals(1, gateway.connectionStarts)

        gateway.finishConnection(ERROR)
        assertEquals(1, scheduler.pendingCount)
        scheduler.runNext()
        assertEquals(2, gateway.connectionStarts)

        gateway.ready = true
        gateway.finishConnection(OK)
        assertEquals(1, readyCallbacks)

        gateway.ready = false
        gateway.disconnect()
        gateway.disconnect()
        assertEquals(1, scheduler.pendingCount)
        scheduler.runNext()
        assertEquals(3, gateway.connectionStarts)
    }

    @Test
    fun `pending purchase remains visible and callback observes the updated state`() {
        val pending = purchase(
            token = "pending-token",
            productId = "sub_monthly",
            state = Purchase.PurchaseState.PENDING,
        )
        val gateway = FakeBillingClientGateway()
        val manager = manager(gateway)
        var callbackTokens = emptyList<String>()
        manager.setPurchaseCallback { _, _ ->
            callbackTokens = manager.activePurchases.value.map(Purchase::getPurchaseToken)
        }

        gateway.updatePurchases(OK, listOf(pending))

        assertEquals(listOf("pending-token"), callbackTokens)
        assertEquals(listOf("pending-token"), manager.activePurchases.value.map(Purchase::getPurchaseToken))
        assertTrue(gateway.acknowledgedTokens.isEmpty())
        assertTrue(gateway.consumedTokens.isEmpty())
    }

    @Test
    fun `already acknowledged purchase is not acknowledged again`() {
        val gateway = FakeBillingClientGateway()
        val manager = manager(gateway)

        gateway.updatePurchases(
            OK,
            listOf(purchase("acknowledged-token", "sub_yearly", acknowledged = true)),
        )

        assertTrue(gateway.acknowledgedTokens.isEmpty())
        assertEquals(
            listOf("acknowledged-token"),
            manager.activePurchases.value.map(Purchase::getPurchaseToken),
        )
    }

    @Test
    fun `failed acknowledgement remains retryable and successful token settles once`() {
        val gateway = FakeBillingClientGateway().apply { acknowledgementResult = ERROR }
        val manager = manager(gateway)
        val purchase = purchase("retry-token", "sub_monthly")

        gateway.updatePurchases(OK, listOf(purchase))
        gateway.acknowledgementResult = OK
        gateway.updatePurchases(OK, listOf(purchase))
        gateway.updatePurchases(OK, listOf(purchase))

        assertEquals(listOf("retry-token", "retry-token"), gateway.acknowledgedTokens)
        assertFalse(gateway.consumedTokens.contains("retry-token"))
    }

    private fun manager(
        gateway: FakeBillingClientGateway,
        scheduler: FakeReconnectScheduler = FakeReconnectScheduler(),
    ): BillingManager = BillingManager(
        clientFactory = BillingClientGatewayFactory { listener ->
            gateway.purchasesUpdatedListener = listener
            gateway
        },
        productPolicy = ProductConfig.PURCHASE_POLICY,
        reconnectScheduler = scheduler,
        retryDelayMillis = { 0L },
    )

    private fun purchase(
        token: String,
        productId: String,
        state: Int = Purchase.PurchaseState.PURCHASED,
        acknowledged: Boolean = false,
    ): Purchase = mockk {
        every { purchaseToken } returns token
        every { products } returns listOf(productId)
        every { purchaseState } returns state
        every { isAcknowledged } returns acknowledged
    }

    private companion object {
        val OK: BillingResult = result(BillingClient.BillingResponseCode.OK, "ok")
        val ERROR: BillingResult = result(BillingClient.BillingResponseCode.ERROR, "error")

        fun result(responseCode: Int, message: String): BillingResult = BillingResult.newBuilder()
            .setResponseCode(responseCode)
            .setDebugMessage(message)
            .build()
    }
}

private class FakeBillingClientGateway : BillingClientGateway {
    var ready = true
    override val isReady: Boolean
        get() = ready

    lateinit var purchasesUpdatedListener: PurchasesUpdatedListener
    private lateinit var connectionListener: BillingClientStateListener
    var connectionStarts = 0
    val queriedProductTypes = mutableListOf<String>()
    val acknowledgedTokens = mutableListOf<String>()
    val consumedTokens = mutableListOf<String>()
    var acknowledgementResult: BillingResult = okResult()
    var consumptionResult: BillingResult = okResult()
    private val queryResponses = mutableMapOf<String, Pair<BillingResult, List<Purchase>>>()

    override fun startConnection(listener: BillingClientStateListener) {
        connectionStarts++
        connectionListener = listener
    }

    fun finishConnection(result: BillingResult) {
        connectionListener.onBillingSetupFinished(result)
    }

    fun disconnect() {
        connectionListener.onBillingServiceDisconnected()
    }

    fun updatePurchases(result: BillingResult, purchases: List<Purchase>?) {
        purchasesUpdatedListener.onPurchasesUpdated(result, purchases)
    }

    fun queryResponse(productType: String, result: BillingResult, purchases: List<Purchase>) {
        queryResponses[productType] = result to purchases
    }

    override fun queryProductDetails(
        productIds: List<String>,
        productType: String,
        callback: (BillingResult, List<ProductDetails>) -> Unit,
    ) {
        callback(okResult(), emptyList())
    }

    override fun queryPurchases(
        productType: String,
        callback: (BillingResult, List<Purchase>) -> Unit,
    ) {
        queriedProductTypes += productType
        val (result, purchases) = queryResponses[productType] ?: (okResult() to emptyList())
        callback(result, purchases)
    }

    override fun acknowledgePurchase(purchaseToken: String, callback: (BillingResult) -> Unit) {
        acknowledgedTokens += purchaseToken
        callback(acknowledgementResult)
    }

    override fun consumePurchase(purchaseToken: String, callback: (BillingResult) -> Unit) {
        consumedTokens += purchaseToken
        callback(consumptionResult)
    }

    override fun launchBillingFlow(
        activity: Activity,
        productParams: BillingFlowParams.ProductDetailsParams,
    ): BillingResult = okResult()

    override fun endConnection() = Unit

    private companion object {
        fun okResult(): BillingResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .setDebugMessage("ok")
            .build()
    }
}

private class FakeReconnectScheduler : BillingReconnectScheduler {
    private val callbacks = ArrayDeque<() -> Unit>()
    val pendingCount: Int
        get() = callbacks.size

    override fun schedule(delayMillis: Long, callback: () -> Unit) {
        callbacks += callback
    }

    override fun cancel() {
        callbacks.clear()
    }

    fun runNext() {
        callbacks.removeFirst().invoke()
    }
}

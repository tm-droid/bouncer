package com.tanush.bouncer.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * [TipBillingGateway] over Google Play Billing. Each [connect] builds a fresh client, because an
 * ended connection cannot be restarted. Not exercised by unit tests: it needs Play Store and
 * Play Console products (see [TipJar] for the logic that is).
 */
class PlayBillingGateway(context: Context) : TipBillingGateway {
    private val appContext = context.applicationContext
    private var client: BillingClient? = null
    private var details: Map<String, ProductDetails> = emptyMap()

    private val updates = MutableSharedFlow<TipPurchaseUpdate>(extraBufferCapacity = UPDATE_BUFFER)
    override val purchaseUpdates: SharedFlow<TipPurchaseUpdate> = updates.asSharedFlow()

    override suspend fun connect(): Int {
        disconnect()
        val built = BillingClient.newBuilder(appContext)
            .setListener { result, purchases ->
                updates.tryEmit(TipPurchaseUpdate(result.responseCode, purchases.orEmpty().map(::toOwned)))
            }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client = built
        return suspendCancellableCoroutine { cont ->
            built.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode)
                }

                // Reconnecting is left to the next screen open: the sheet is short-lived.
                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(BillingResponseCode.SERVICE_DISCONNECTED)
                }
            })
        }
    }

    override suspend fun queryTiers(productIds: List<String>): TipQuery {
        val c = client ?: return TipQuery(BillingResponseCode.SERVICE_DISCONNECTED, emptyList())
        val products = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val result = c.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(products).build())
        details = result.productDetailsList.orEmpty().associateBy { it.productId }
        val tiers = details.values.mapNotNull { d ->
            d.oneTimePurchaseOfferDetails?.formattedPrice?.let { TipTier(d.productId, it) }
        }
        return TipQuery(result.billingResult.responseCode, tiers)
    }

    override suspend fun queryOwned(): TipOwned {
        val c = client ?: return TipOwned(BillingResponseCode.SERVICE_DISCONNECTED, emptyList())
        val result = c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        return TipOwned(result.billingResult.responseCode, result.purchasesList.map(::toOwned))
    }

    override suspend fun launch(activity: Activity, productId: String): Int {
        val c = client ?: return BillingResponseCode.SERVICE_DISCONNECTED
        val product = details[productId] ?: return BillingResponseCode.ITEM_UNAVAILABLE
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()),
            )
            .build()
        return c.launchBillingFlow(activity, params).responseCode
    }

    override suspend fun consume(token: String): Int {
        val c = client ?: return BillingResponseCode.SERVICE_DISCONNECTED
        return c.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(token).build()).billingResult.responseCode
    }

    override fun disconnect() {
        client?.endConnection()
        client = null
        details = emptyMap()
    }

    // A purchase covers one product here; tips are bought one at a time.
    private fun toOwned(p: Purchase) = OwnedTip(
        productId = p.products.firstOrNull().orEmpty(),
        token = p.purchaseToken,
        purchased = p.purchaseState == Purchase.PurchaseState.PURCHASED,
    )

    private companion object {
        const val UPDATE_BUFFER = 8
    }
}

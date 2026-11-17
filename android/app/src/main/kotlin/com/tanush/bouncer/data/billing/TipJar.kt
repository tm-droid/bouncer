package com.tanush.bouncer.data.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Play Console product ids of the three consumable tips, smallest first. */
internal val TIP_PRODUCT_IDS = listOf("tip_small", "tip_medium", "tip_large")

/** One tip amount. [price] is Play's own formatted string for the user's country — never hardcoded. */
data class TipTier(val productId: String, val price: String)

/** A tip purchase Play reports as owned. [purchased] is false while the payment is still pending. */
data class OwnedTip(val productId: String, val token: String, val purchased: Boolean)

data class TipQuery(val code: Int, val tiers: List<TipTier>)

data class TipOwned(val code: Int, val tips: List<OwnedTip>)

/** What Play pushes after a purchase flow: a response [code] and whatever purchases it covers. */
data class TipPurchaseUpdate(val code: Int, val tips: List<OwnedTip>)

/** The slice of Play Billing the tip jar needs, so the state machine runs without Play in tests. */
interface TipBillingGateway {
    val purchaseUpdates: Flow<TipPurchaseUpdate>

    /** A [BillingResponseCode]. */
    suspend fun connect(): Int
    suspend fun queryTiers(productIds: List<String>): TipQuery
    suspend fun queryOwned(): TipOwned
    suspend fun launch(activity: Activity, productId: String): Int
    suspend fun consume(token: String): Int
    fun disconnect()
}

enum class TipUnavailableReason {
    /** Play Billing cannot run here: no Play Store, an emulator image without it, or a country it skips. */
    NOT_SUPPORTED,

    /** Play answered but has no tip products: not created in Play Console, or not yet active. */
    NO_PRODUCTS,

    /** Play could not be reached right now; worth retrying. */
    NOT_CONNECTED,
}

sealed interface TipJarState {
    data object Loading : TipJarState
    data class Unavailable(val reason: TipUnavailableReason) : TipJarState

    /** [pending] is true while an earlier tip waits on its payment; it is consumed once it clears. */
    data class Ready(val tiers: List<TipTier>, val pending: Boolean = false) : TipJarState
    data class Purchasing(val tiers: List<TipTier>) : TipJarState
    data class Thanks(val tiers: List<TipTier>) : TipJarState

    /** [tiers] is empty when the failure came before the prices loaded. */
    data class Error(val tiers: List<TipTier>) : TipJarState
}

/**
 * The optional "tip the developer" flow. Nothing is gated behind it.
 *
 * Connects when [open] is called and drops the connection on [close]. A tip is a consumable: the
 * purchase is consumed once paid so the same amount can be tipped again, and unconsumed owned tips
 * are consumed on connect (a crash between payment and consume would otherwise block that tier,
 * and Play refunds purchases left unacknowledged after three days).
 */
class TipJar(
    private val gateway: TipBillingGateway,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<TipJarState>(TipJarState.Loading)
    val state: StateFlow<TipJarState> = _state.asStateFlow()

    private var session: Job? = null
    private var tiers: List<TipTier> = emptyList()

    fun open() {
        if (session?.isActive == true) return
        _state.value = TipJarState.Loading
        session = scope.launch {
            launch { gateway.purchaseUpdates.collect { onUpdate(it) } }
            load()
        }
    }

    fun close() {
        session?.cancel()
        session = null
        gateway.disconnect()
        tiers = emptyList()
        _state.value = TipJarState.Loading
    }

    /** Drops the connection and starts over: the retry for [TipJarState.Unavailable] and [TipJarState.Error]. */
    fun reload() {
        close()
        open()
    }

    fun tipAgain() {
        val current = _state.value
        if (current is TipJarState.Thanks) _state.value = TipJarState.Ready(current.tiers)
    }

    fun tip(activity: Activity, productId: String) {
        val ready = _state.value as? TipJarState.Ready ?: return
        if (ready.tiers.none { it.productId == productId }) return
        _state.value = TipJarState.Purchasing(ready.tiers)
        scope.launch {
            val code = gateway.launch(activity, productId)
            if (session?.isActive != true || _state.value !is TipJarState.Purchasing) return@launch
            when (code) {
                // The outcome arrives on purchaseUpdates.
                BillingResponseCode.OK -> Unit
                BillingResponseCode.USER_CANCELED -> _state.value = TipJarState.Ready(tiers)
                BillingResponseCode.ITEM_ALREADY_OWNED -> recoverOwned()
                else -> _state.value = TipJarState.Error(tiers)
            }
        }
    }

    private suspend fun load() {
        val connected = gateway.connect()
        if (connected != BillingResponseCode.OK) return unavailable(connected)
        val query = gateway.queryTiers(TIP_PRODUCT_IDS)
        if (query.code != BillingResponseCode.OK) return unavailable(query.code)
        tiers = query.tiers.filter { it.productId in TIP_PRODUCT_IDS }.sortedBy { TIP_PRODUCT_IDS.indexOf(it.productId) }
        if (tiers.isEmpty()) {
            _state.value = TipJarState.Unavailable(TipUnavailableReason.NO_PRODUCTS)
            return
        }
        recoverOwned()
    }

    private fun unavailable(code: Int) {
        val reason = when (code) {
            BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingResponseCode.BILLING_UNAVAILABLE,
            BillingResponseCode.DEVELOPER_ERROR -> TipUnavailableReason.NOT_SUPPORTED
            else -> TipUnavailableReason.NOT_CONNECTED
        }
        _state.value = TipJarState.Unavailable(reason)
    }

    /** Consumes every paid tip Play still lists as owned; pending ones are left for a later connect. */
    private suspend fun recoverOwned() {
        val owned = gateway.queryOwned()
        if (owned.code != BillingResponseCode.OK) {
            _state.value = TipJarState.Ready(tiers)
            return
        }
        settle(owned.tips, failureIsError = false)
    }

    private suspend fun onUpdate(update: TipPurchaseUpdate) {
        when (update.code) {
            BillingResponseCode.OK -> settle(update.tips, failureIsError = true)
            BillingResponseCode.USER_CANCELED -> _state.value = TipJarState.Ready(tiers)
            BillingResponseCode.ITEM_ALREADY_OWNED -> recoverOwned()
            else -> _state.value = TipJarState.Error(tiers)
        }
    }

    /**
     * Consumes the paid tips in [tips] and picks the resulting state. A consume that fails right
     * after a purchase is an error the user should see; during recovery it is retried next connect.
     */
    private suspend fun settle(tips: List<OwnedTip>, failureIsError: Boolean) {
        val mine = tips.filter { it.productId in TIP_PRODUCT_IDS }
        val paid = mine.filter { it.purchased }
        val consumed = paid.count { gateway.consume(it.token) == BillingResponseCode.OK }
        _state.value = when {
            consumed > 0 -> TipJarState.Thanks(tiers)
            paid.isNotEmpty() && failureIsError -> TipJarState.Error(tiers)
            else -> TipJarState.Ready(tiers, pending = mine.any { !it.purchased })
        }
    }
}

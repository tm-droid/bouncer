package com.tanush.bouncer.data.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TipJarTest {

    private class FakeGateway : TipBillingGateway {
        override val purchaseUpdates = MutableSharedFlow<TipPurchaseUpdate>(extraBufferCapacity = 8)
        var connectCode = BillingResponseCode.OK
        var queryCode = BillingResponseCode.OK
        var tiers = listOf(TipTier("tip_small", "$0.99"), TipTier("tip_medium", "$2.99"), TipTier("tip_large", "$4.99"))
        var owned = mutableListOf<OwnedTip>()
        var launchCode = BillingResponseCode.OK
        var consumeCode = BillingResponseCode.OK
        val consumed = mutableListOf<String>()
        var disconnects = 0

        override suspend fun connect() = connectCode
        override suspend fun queryTiers(productIds: List<String>) = TipQuery(queryCode, tiers)
        override suspend fun queryOwned() = TipOwned(BillingResponseCode.OK, owned.toList())
        override suspend fun launch(activity: Activity, productId: String) = launchCode
        override suspend fun consume(token: String): Int {
            if (consumeCode == BillingResponseCode.OK) consumed += token
            return consumeCode
        }
        override fun disconnect() { disconnects++ }
    }

    private val activity = mockk<Activity>()

    private fun TestScope.jar(gateway: FakeGateway) =
        TipJar(gateway, backgroundScope + UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.opened(gateway: FakeGateway) = jar(gateway).also { it.open() }

    @Test
    fun opensReadyWithPricesFromPlay() = runTest {
        val jar = opened(FakeGateway())
        val state = jar.state.value as TipJarState.Ready
        assertEquals(listOf("$0.99", "$2.99", "$4.99"), state.tiers.map { it.price })
        assertEquals(false, state.pending)
    }

    @Test
    fun tiersAreOrderedSmallestFirstAndForeignProductsDropped() = runTest {
        val g = FakeGateway().apply { tiers = listOf(TipTier("tip_large", "L"), TipTier("other", "X"), TipTier("tip_small", "S")) }
        val state = opened(g).state.value as TipJarState.Ready
        assertEquals(listOf("tip_small", "tip_large"), state.tiers.map { it.productId })
    }

    @Test
    fun unsupportedBillingIsUnavailable() = runTest {
        val g = FakeGateway().apply { connectCode = BillingResponseCode.BILLING_UNAVAILABLE }
        assertEquals(TipJarState.Unavailable(TipUnavailableReason.NOT_SUPPORTED), opened(g).state.value)
    }

    @Test
    fun serviceFailureIsNotConnected() = runTest {
        val g = FakeGateway().apply { connectCode = BillingResponseCode.SERVICE_UNAVAILABLE }
        assertEquals(TipJarState.Unavailable(TipUnavailableReason.NOT_CONNECTED), opened(g).state.value)
    }

    @Test
    fun queryFailureIsUnavailable() = runTest {
        val g = FakeGateway().apply { queryCode = BillingResponseCode.SERVICE_DISCONNECTED }
        assertEquals(TipJarState.Unavailable(TipUnavailableReason.NOT_CONNECTED), opened(g).state.value)
    }

    @Test
    fun missingProductsIsUnavailable() = runTest {
        val g = FakeGateway().apply { tiers = emptyList() }
        assertEquals(TipJarState.Unavailable(TipUnavailableReason.NO_PRODUCTS), opened(g).state.value)
    }

    @Test
    fun paidPurchaseIsConsumedThenThanks() = runTest {
        val g = FakeGateway()
        val jar = opened(g)
        jar.tip(activity, "tip_medium")
        assertTrue(jar.state.value is TipJarState.Purchasing)
        g.purchaseUpdates.emit(TipPurchaseUpdate(BillingResponseCode.OK, listOf(OwnedTip("tip_medium", "tok1", true))))
        assertTrue(jar.state.value is TipJarState.Thanks)
        assertEquals(listOf("tok1"), g.consumed)
        jar.tipAgain()
        assertTrue(jar.state.value is TipJarState.Ready)
    }

    @Test
    fun userCancelGoesBackToReady() = runTest {
        val g = FakeGateway()
        val jar = opened(g)
        jar.tip(activity, "tip_small")
        g.purchaseUpdates.emit(TipPurchaseUpdate(BillingResponseCode.USER_CANCELED, emptyList()))
        assertTrue(jar.state.value is TipJarState.Ready)
        assertTrue(g.consumed.isEmpty())
    }

    @Test
    fun cancelReportedByLaunchGoesBackToReady() = runTest {
        val g = FakeGateway().apply { launchCode = BillingResponseCode.USER_CANCELED }
        val jar = opened(g)
        jar.tip(activity, "tip_small")
        assertTrue(jar.state.value is TipJarState.Ready)
    }

    @Test
    fun launchFailureIsError() = runTest {
        val g = FakeGateway().apply { launchCode = BillingResponseCode.ERROR }
        val jar = opened(g)
        jar.tip(activity, "tip_small")
        assertTrue(jar.state.value is TipJarState.Error)
    }

    @Test
    fun pendingPurchaseShowsPendingAndIsNotConsumed() = runTest {
        val g = FakeGateway()
        val jar = opened(g)
        jar.tip(activity, "tip_large")
        g.purchaseUpdates.emit(TipPurchaseUpdate(BillingResponseCode.OK, listOf(OwnedTip("tip_large", "tok2", false))))
        assertEquals(true, (jar.state.value as TipJarState.Ready).pending)
        assertTrue(g.consumed.isEmpty())
    }

    @Test
    fun pendingTipThatClearedIsConsumedOnNextOpen() = runTest {
        val g = FakeGateway().apply { owned += OwnedTip("tip_large", "tok2", false) }
        val first = opened(g)
        assertEquals(true, (first.state.value as TipJarState.Ready).pending)
        g.owned[0] = OwnedTip("tip_large", "tok2", true)
        first.reload()
        assertTrue(first.state.value is TipJarState.Thanks)
        assertEquals(listOf("tok2"), g.consumed)
    }

    @Test
    fun unconsumedPurchaseIsRecoveredOnConnect() = runTest {
        val g = FakeGateway().apply { owned += OwnedTip("tip_small", "crashed", true) }
        val jar = opened(g)
        assertEquals(listOf("crashed"), g.consumed)
        assertTrue(jar.state.value is TipJarState.Thanks)
    }

    @Test
    fun ownedNonTipPurchasesAreLeftAlone() = runTest {
        val g = FakeGateway().apply { owned += OwnedTip("premium", "x", true) }
        val jar = opened(g)
        assertTrue(g.consumed.isEmpty())
        assertTrue(jar.state.value is TipJarState.Ready)
    }

    @Test
    fun alreadyOwnedRecoversByConsuming() = runTest {
        val g = FakeGateway().apply { launchCode = BillingResponseCode.ITEM_ALREADY_OWNED }
        val jar = opened(g)
        g.owned += OwnedTip("tip_small", "stuck", true)
        jar.tip(activity, "tip_small")
        assertEquals(listOf("stuck"), g.consumed)
        assertTrue(jar.state.value is TipJarState.Thanks)
    }

    @Test
    fun consumeFailureAfterPurchaseIsError() = runTest {
        val g = FakeGateway().apply { consumeCode = BillingResponseCode.SERVICE_UNAVAILABLE }
        val jar = opened(g)
        jar.tip(activity, "tip_small")
        g.purchaseUpdates.emit(TipPurchaseUpdate(BillingResponseCode.OK, listOf(OwnedTip("tip_small", "t", true))))
        assertTrue(jar.state.value is TipJarState.Error)
    }

    @Test
    fun consumeFailureDuringRecoveryStaysReady() = runTest {
        val g = FakeGateway().apply { consumeCode = BillingResponseCode.SERVICE_UNAVAILABLE; owned += OwnedTip("tip_small", "t", true) }
        assertTrue(opened(g).state.value is TipJarState.Ready)
    }

    @Test
    fun tipIsIgnoredUnlessReady() = runTest {
        val g = FakeGateway().apply { connectCode = BillingResponseCode.BILLING_UNAVAILABLE }
        val jar = opened(g)
        jar.tip(activity, "tip_small")
        assertTrue(jar.state.value is TipJarState.Unavailable)
    }

    @Test
    fun closeEndsConnectionAndResets() = runTest {
        val g = FakeGateway()
        val jar = opened(g)
        jar.close()
        assertEquals(1, g.disconnects)
        assertEquals(TipJarState.Loading, jar.state.value)
    }

    @Test
    fun errorUpdateShowsError() = runTest {
        val g = FakeGateway()
        val jar = opened(g)
        jar.tip(activity, "tip_small")
        g.purchaseUpdates.emit(TipPurchaseUpdate(BillingResponseCode.ERROR, emptyList()))
        assertTrue(jar.state.value is TipJarState.Error)
    }
}

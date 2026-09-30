package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.models.CricketMatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rules that keep an open match screen from billing Appwrite for nothing: its Realtime
 * streams stop while the app is backgrounded (and catch up on return), and a fixture far from its
 * start does not ping presence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundGateTest {

    private fun match(status: String, currentStatus: String = "", startDateMs: Long? = null) = CricketMatch(
        id = "1",
        seriesId = "0",
        matchTitle = "A vs B",
        matchType = "T20",
        status = status,
        teams = emptyList(),
        venue = "",
        date = "",
        matchTime = "",
        score = null,
        currentStatus = currentStatus,
        info = "",
        startDateMs = startDateMs,
    )

    @Test
    fun `a fixture more than the lead away does not ping, one inside it does`() {
        val now = 1_000_000_000L
        val far = match("Upcoming", startDateMs = now + PRESENCE_LEAD_MS + 1)
        val near = match("Upcoming", startDateMs = now + PRESENCE_LEAD_MS)
        require(far.isUpcoming) { "fixture must classify as upcoming" }
        assertFalse(shouldPingPresence(far, now))
        assertTrue(shouldPingPresence(near, now))
    }

    @Test
    fun `an unknown start time keeps pinging`() {
        val m = match("Upcoming", startDateMs = null)
        require(m.isUpcoming) { "fixture must classify as upcoming" }
        assertTrue(shouldPingPresence(m, 0L))
    }

    @Test
    fun `a match that is not upcoming always pings`() {
        val live = match("Live", "India need 20 runs in 12 balls", startDateMs = Long.MAX_VALUE)
        require(!live.isUpcoming) { "fixture must not classify as upcoming" }
        assertTrue(shouldPingPresence(live, 0L))
    }

    @Test
    fun `source is collected only while visible, and catch-up runs on every return but not the first start`() =
        runTest(UnconfinedTestDispatcher()) {
            val visible = MutableStateFlow(true)
            var subscriptions = 0
            var catchUps = 0
            val out = mutableListOf<String>()
            val job = launch {
                whileForeground(
                    visible,
                    catchUp = { catchUps++; listOf("missed") },
                ) { subscriptions++; flowOf("live") }.toList(out)
            }
            visible.value = false
            visible.value = true
            job.cancel()

            assertEquals("one subscription per visible span", 2, subscriptions)
            assertEquals("catch-up only on the return", 1, catchUps)
            assertEquals(listOf("live", "missed", "live"), out)
        }
}

package com.tanush.bouncer.services

import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.data.remote.FunctionOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * An open match screen and the Live Update service both heartbeat. A match that is open and
 * followed must cost one execution per window, not two.
 */
class MatchPresenceDedupeTest {

    private var now = 1_800_000_000_000L

    @Before
    fun setUp() {
        mockkObject(AppwriteClient)
        MatchPresenceReporter.forgetPings()
        MatchPresenceReporter.clock = { now }
    }

    @After
    fun tearDown() {
        MatchPresenceReporter.clock = System::currentTimeMillis
        MatchPresenceReporter.forgetPings()
        unmockkAll()
    }

    private fun accept() {
        coEvery { AppwriteClient.executeFunctionDetailed(any(), any()) } returns FunctionOutcome.Success("{}")
    }

    @Test
    fun `a match pinged a moment ago is not pinged again by the other heartbeat`() = runTest {
        accept()
        assertTrue(MatchPresenceReporter.ping("1"))
        now += 5_000
        // Reported as accepted: the server does hold a fresh stamp for it.
        assertTrue(MatchPresenceReporter.pingAll(listOf("1")))
        coVerify(exactly = 1) { AppwriteClient.executeFunctionDetailed(any(), any()) }
    }

    @Test
    fun `the batch still carries the matches that were not just pinged`() = runTest {
        accept()
        MatchPresenceReporter.ping("1")
        now += 5_000
        MatchPresenceReporter.pingAll(listOf("1", "2"))
        coVerify(exactly = 1) { AppwriteClient.executeFunctionDetailed(any(), match { it.contains("\"2\"") && !it.contains("\"1\"") }) }
    }

    @Test
    fun `the window closes, and a rejected ping opens none`() = runTest {
        coEvery { AppwriteClient.executeFunctionDetailed(any(), any()) } returns FunctionOutcome.Unavailable
        assertFalse(MatchPresenceReporter.ping("1"))
        assertFalse(MatchPresenceReporter.pingedRecently("1", now))

        accept()
        assertTrue(MatchPresenceReporter.ping("1"))
        assertTrue(MatchPresenceReporter.pingedRecently("1", now + MatchPresenceReporter.RECENT_PING_MS - 1))
        assertFalse(MatchPresenceReporter.pingedRecently("1", now + MatchPresenceReporter.RECENT_PING_MS))
        now += MatchPresenceReporter.RECENT_PING_MS
        MatchPresenceReporter.ping("1")
        coVerify(exactly = 3) { AppwriteClient.executeFunctionDetailed(any(), any()) }
    }
}

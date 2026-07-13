package com.tanush.bouncer.data.remote

import io.appwrite.Client
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/**
 * Locks the blank-commentary guard in [AppwriteAICache.set]: a provider returning "" is a
 * failure, not a value, and must never be cached (that pollution is the root cause of the
 * empty ai-commentary-cache rows found in the DB audit).
 *
 * The guard is verified at a stable seam — the write path reads `AppwriteClient.client`
 * (via the base `tablesDB` getter). A blank input must return before touching it; a real
 * value must reach it. This avoids brittle mocking of the Appwrite SDK's method signatures.
 */
class AppwriteAICacheTest {

    @After
    fun tearDown() = unmockkAll()

    private fun stubReadyClient() {
        mockkObject(AppwriteClient)
        every { AppwriteClient.client } returns mockk<Client>(relaxed = true)
        mockkObject(AppwriteAICache)
        every { AppwriteAICache.isReady } returns true
    }

    @Test
    fun `blank commentary is never written`() = runTest {
        stubReadyClient()

        AppwriteAICache.set("k1", "")
        AppwriteAICache.set("k2", "   \n\t ")

        // Guard short-circuits before the write path — the SDK client is never accessed.
        verify(exactly = 0) { AppwriteClient.client }
    }

    @Test
    fun `non-blank commentary enters the write path`() = runTest {
        stubReadyClient()

        AppwriteAICache.set("k3", "Kohli drives it through the covers for four.")

        // A real value proves the guard is blank-specific, not a blanket no-op: the write
        // path is entered and reads the SDK client.
        verify(atLeast = 1) { AppwriteClient.client }
    }
}

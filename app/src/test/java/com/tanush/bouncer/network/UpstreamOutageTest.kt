package com.tanush.bouncer.network

import com.tanush.bouncer.data.AppException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException

class UpstreamOutageTest {

    @Test
    fun `a 5xx from a scraped page is the site's outage`() {
        assertTrue(isUpstreamOutage(ScrapeHttpException(504, "https://www.cricbuzz.com/cricket-scorecard-archives/2026")))
    }

    @Test
    fun `it is still an outage once a repository has wrapped it`() {
        // ANDROID-56 arrived as NetworkException("Network error") caused by the 504.
        val wrapped = AppException.NetworkException(cause = ScrapeHttpException(504, "https://www.cricbuzz.com/x"))
        assertTrue(isUpstreamOutage(wrapped))
    }

    @Test
    fun `a 4xx is not an outage`() {
        assertFalse(isUpstreamOutage(ScrapeHttpException(404, "https://www.cricbuzz.com/x")))
    }

    @Test
    fun `an IOException that merely says 504 is not one`() {
        assertFalse(isUpstreamOutage(IOException("HTTP 504 fetching https://www.cricbuzz.com/x")))
    }

    @Test
    fun `the message is the one the plain IOException carried`() {
        assertTrue(ScrapeHttpException(504, "https://h/x").message == "HTTP 504 fetching https://h/x")
    }

    @Test
    fun `a connection closed before the status line is a connectivity failure`() {
        // ANDROID-5A, as OkHttp raises it.
        val dropped = IOException("unexpected end of stream on https://www.cricbuzz.com/...", EOFException("\\n not found: limit=0"))
        assertTrue(HttpClientProvider.isConnectivityException(dropped))
    }
}

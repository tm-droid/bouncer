package com.tanush.bouncer.ui.components.v2

import com.tanush.bouncer.data.AppException
import com.tanush.bouncer.network.ScrapeHttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStatesTest {
    @Test fun dnsFailureIsOffline() =
        assertEquals(FailureKind.Offline, failureKindOf(UnknownHostException("x")))

    @Test fun wrappedNetworkErrorIsOffline() =
        assertEquals(FailureKind.Offline, failureKindOf(IOException(AppException.NetworkException())))

    @Test fun upstreamHttpAndTimeoutAreServer() {
        assertEquals(FailureKind.Server, failureKindOf(IOException(ScrapeHttpException(504, "u"))))
        assertEquals(FailureKind.Server, failureKindOf(SocketTimeoutException()))
        assertEquals(FailureKind.Server, failureKindOf(AppException.ApiException(500)))
    }

    @Test fun anythingElseIsUnknown() =
        assertEquals(FailureKind.Unknown, failureKindOf(IllegalStateException("boom")))

    @Test fun contentWithFailureIsBanner() =
        assertEquals(
            ScreenState.ContentWithBanner(FailureKind.Offline, 5L),
            screenStateOf(true, false, FailureKind.Offline, 5L),
        )

    @Test fun refreshKeepsContent() =
        assertEquals(ScreenState.Content, screenStateOf(true, true, null))

    @Test fun noContentPicksLoadingThenFailedThenEmpty() {
        assertEquals(ScreenState.Loading, screenStateOf(false, true, null))
        assertEquals(ScreenState.Failed(FailureKind.Server), screenStateOf(false, false, FailureKind.Server))
        assertEquals(ScreenState.Empty, screenStateOf(false, false, null))
    }

    @Test fun skeletonWaits200ms() {
        assertFalse(skeletonVisible(true, 199, null))
        assertTrue(skeletonVisible(true, 200, null))
        assertFalse(skeletonVisible(false, 500, null))
    }

    @Test fun shownSkeletonStaysAtLeast300ms() {
        assertTrue(skeletonVisible(false, 0, 299))
        assertFalse(skeletonVisible(false, 0, 300))
        assertTrue(skeletonVisible(true, 0, 1000))
    }
}

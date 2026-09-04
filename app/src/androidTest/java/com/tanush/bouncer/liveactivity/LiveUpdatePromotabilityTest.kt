package com.tanush.bouncer.liveactivity

import android.app.Notification
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asks the platform whether the Live Update we build is actually promotable.
 *
 * This test exists because of exactly the bug it now guards. The stack this replaced requested
 * promotion by reflection while also setting `DecoratedCustomViewStyle` and two `RemoteViews`,
 * which disqualifies a notification outright — so the request could never be honoured, and nothing
 * anywhere said so. `Notification.hasPromotableCharacteristics()` is the system's own verdict, and
 * no unit test can stand in for it: the project has no Robolectric, and the promotion rules live
 * in the framework rather than in `NotificationCompat`.
 *
 * Run with `./gradlew connectedAndroidTest` on an API 36+ device or emulator. The assertions skip
 * below API 36, where promotion does not exist and the same builder is expected to degrade to an
 * ordinary ongoing notification.
 */
@RunWith(AndroidJUnit4::class)
class LiveUpdatePromotabilityTest {

    private lateinit var notifier: LiveUpdateNotifier

    private val content = LiveUpdateContent(
        matchId = "170667",
        subText = "The Ashes · Test",
        title = "ENG 348  ·  AUS 266 & 234/1",
        text = "Warner 95(159)* · Labuschagne 63(103)",
        shortCriticalText = "234/1",
        progress = progressFor(
            format = com.tanush.bouncer.analysis.CricketConstants.MatchFormat.T20,
            overs = "12.3",
            wickets = 2,
            fallOfWicketOvers = listOf("4.2", "11.5"),
        ),
        accentTeamName = "Australia",
        logoTeamName = "Australia",
        isComplete = false,
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        notifier = LiveUpdateNotifier(context)
        notifier.ensureChannel()
    }

    @Test
    fun theLiveUpdateSatisfiesThePlatformsPromotionRules() {
        assumeTrue("Live Updates are API 36+", Build.VERSION.SDK_INT >= PROMOTED_MIN_SDK)
        val notification = notifier.build(content, largeIcon = null)
        assertTrue(
            "the platform refused to treat this as promotable — check style, ongoing, and custom views",
            notification.hasPromotableCharacteristics(),
        )
    }

    /** The single disqualifier the old stack tripped over. */
    @Test
    fun theLiveUpdateCarriesNoCustomRemoteViews() {
        val notification = notifier.build(content, largeIcon = null)
        assertNull(notification.contentView)
        assertNull(notification.bigContentView)
        assertNull(notification.headsUpContentView)
    }

    /**
     * Read through `extras` rather than `Notification.isRequestPromotedOngoing()`: the getter
     * arrived after the setter did, and calling it on a stock API 36 build is a
     * `NoSuchMethodError`. The extra is what `NotificationCompat` writes and what the platform
     * reads, on every version that has the feature at all.
     */
    @Test
    fun theLiveUpdateIsOngoingAndRequestsPromotion() {
        val notification = notifier.build(content, largeIcon = null)
        assertTrue("must be ongoing", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assumeTrue("promotion is API 36+", Build.VERSION.SDK_INT >= PROMOTED_MIN_SDK)
        assertTrue(
            "must request promotion",
            notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING),
        )
    }

    /** A group summary cannot be promoted, so this one must never become one. */
    @Test
    fun theLiveUpdateIsNotAGroupSummary() {
        val notification = notifier.build(content, largeIcon = null)
        assertFalse(notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
    }

    /**
     * An indeterminate bar is what a match with no readable over figure renders, and it must stay
     * promotable — otherwise the first notification of every match, built before any payload
     * arrives, would silently fail to promote.
     */
    @Test
    fun anIndeterminateBarIsStillPromotable() {
        assumeTrue("Live Updates are API 36+", Build.VERSION.SDK_INT >= PROMOTED_MIN_SDK)
        val placeholder = content.copy(
            progress = LiveUpdateProgress.Indeterminate,
            shortCriticalText = null,
        )
        assertTrue(notifier.build(placeholder, largeIcon = null).hasPromotableCharacteristics())
    }

    private companion object {
        const val PROMOTED_MIN_SDK = 36
    }
}

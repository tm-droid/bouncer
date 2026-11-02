package com.tanush.bouncer.liveactivity

import android.app.Notification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The other half of the promotion trade, asserted on the device.
 *
 * `LiveUpdatePromotabilityTest` proves the template card is promotable where promotion exists. This
 * proves that where it does **not** exist the app stops paying for it: a promoted notification may
 * not carry `RemoteViews` (measured — `setCustomContentView`, `setCustomBigContentView`, either
 * alongside a style, and `DecoratedCustomViewStyle` all make `hasPromotableCharacteristics()`
 * false), so a device that cannot promote should be getting the designed layout instead of a
 * template it gains nothing from.
 *
 * Getting this wrong is silent in exactly the way the rest of this feature was: the card still
 * posts, still updates, and simply looks worse than it needed to for every user below Android 16.
 */
@RunWith(AndroidJUnit4::class)
class LiveUpdateScoreCardTest {

    private lateinit var notifier: LiveUpdateNotifier

    private val content = LiveUpdateContent(
        matchId = "169981",
        subText = "South Africa vs Namibia · ODI",
        title = "NAM 31/4 (9.5)",
        text = "Loftie-Eaton 5(7)* · Erasmus 12(17)",
        bigText = "RSA 335/9\nLoftie-Eaton 5 (7)*",
        scoreboard = LiveUpdateScoreboard(
            battingName = "NAM",
            battingScore = "31/4",
            battingOvers = "9.5",
            opponentName = "RSA",
            opponentScore = "335/9",
            batters = listOf(
                LiveUpdateScoreboard.PlayerLine("Jan Nicol Loftie-Eaton", "5 (7)", isStriker = true),
                LiveUpdateScoreboard.PlayerLine("Gerhard Erasmus", "12 (17)"),
            ),
            bowler = LiveUpdateScoreboard.PlayerLine("Eathan Bosch", "2/19 (4.5)"),
            recentBalls = listOf("W", "1", "4", "1", "0", "0"),
            statusLine = "Need 304 runs · RRR 7.59",
        ),
        shortCriticalText = "31/4",
        accentTeamName = "Namibia",
        logoTeamName = "Namibia",
        opponentLogoTeamName = "South Africa",
        isComplete = false,
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        notifier = LiveUpdateNotifier(context)
        notifier.ensureChannel()
        assumeFalse("device can promote; the template card is correct there", notifier.canPromote())
    }

    @Test
    fun aDeviceThatCannotPromoteGetsTheDesignedLayout() {
        val notification = notifier.build(content, largeIcon = null, opponentIcon = null)
        assertNotNull("collapsed custom view missing", notification.contentView)
        assertNotNull("expanded custom view missing", notification.bigContentView)
    }

    /** Nothing is lost by the swap: it is still the ongoing card a foreground service can anchor. */
    @Test
    fun theDesignedCardIsStillAnOngoingStatusNotification() {
        val notification = notifier.build(content, largeIcon = null, opponentIcon = null)
        assertTrue("must be ongoing", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        assertTrue(Notification.CATEGORY_STATUS == notification.category)
    }

    /**
     * The placeholder is built before any payload arrives and has an empty scoreboard. It must
     * still inflate rather than throwing inside `RemoteViews`, which would take the foreground
     * service's `startForeground` down with it.
     */
    @Test
    fun theEmptyPlaceholderStillBuilds() {
        val placeholder = content.copy(
            scoreboard = LiveUpdateScoreboard(null, null, null, null, null, emptyList(), null, emptyList(), null),
            bigText = "",
        )
        assertNotNull(notifier.build(placeholder, largeIcon = null).bigContentView)
    }
}

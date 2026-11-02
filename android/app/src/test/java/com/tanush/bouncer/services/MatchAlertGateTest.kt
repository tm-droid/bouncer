package com.tanush.bouncer.services

import com.tanush.bouncer.managers.MatchAlertPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchAlertGateTest {

    private val all = MatchAlertPrefs(wickets = true, boundaries = true, milestones = true, startAndResult = true)
    private val on = GlobalAlertSwitches()

    @Test
    fun `every server type is shown when everything allows it`() {
        for (type in MatchAlertType.ALL) {
            assertTrue(type, shouldShowMatchAlert(type, all, on, quietHours = false))
        }
    }

    @Test
    fun `the match's own switch decides each type`() {
        assertFalse(shouldShowMatchAlert(MatchAlertType.WICKET, all.copy(wickets = false), on, false))
        assertFalse(shouldShowMatchAlert(MatchAlertType.BOUNDARY, all.copy(boundaries = false), on, false))
        assertFalse(shouldShowMatchAlert(MatchAlertType.MILESTONE, all.copy(milestones = false), on, false))
        assertFalse(shouldShowMatchAlert(MatchAlertType.MATCH_START, all.copy(startAndResult = false), on, false))
        assertFalse(shouldShowMatchAlert(MatchAlertType.MATCH_END, all.copy(startAndResult = false), on, false))
    }

    @Test
    fun `an unfollowed match shows nothing`() {
        for (type in MatchAlertType.ALL) {
            assertFalse(type, shouldShowMatchAlert(type, MatchAlertPrefs.NONE, on, false))
        }
    }

    @Test
    fun `global switches veto`() {
        for (type in MatchAlertType.ALL) {
            assertFalse(type, shouldShowMatchAlert(type, all, on.copy(matchAlerts = false), false))
        }
        assertFalse(shouldShowMatchAlert(MatchAlertType.WICKET, all, on.copy(wickets = false), false))
        assertFalse(shouldShowMatchAlert(MatchAlertType.BOUNDARY, all, on.copy(boundaries = false), false))
        assertTrue(shouldShowMatchAlert(MatchAlertType.MILESTONE, all, on.copy(wickets = false, boundaries = false), false))
    }

    @Test
    fun `quiet hours veto everything`() {
        for (type in MatchAlertType.ALL) {
            assertFalse(type, shouldShowMatchAlert(type, all, on, quietHours = true))
        }
    }

    @Test
    fun `an unknown type is dropped`() {
        assertFalse(shouldShowMatchAlert("score_update", all, on, false))
        assertFalse(shouldShowMatchAlert("MATCH_STARTED", all, on, false))
    }

    @Test
    fun `following turns boundaries off by default`() {
        assertTrue(MatchAlertPrefs.DEFAULT.wickets)
        assertFalse(MatchAlertPrefs.DEFAULT.boundaries)
        assertTrue(MatchAlertPrefs.DEFAULT.anyOn)
        assertFalse(MatchAlertPrefs.NONE.anyOn)
    }

    @Test
    fun `a push past its age is stale, and one of unknown age is not`() {
        val now = 1_800_000_000_000L
        assertFalse(isAlertStale(MatchAlertType.WICKET, now - 60_000, now))
        assertTrue(isAlertStale(MatchAlertType.WICKET, now - 6 * 60_000, now))
        // A result is still news hours later.
        assertFalse(isAlertStale(MatchAlertType.MATCH_END, now - 2 * 60 * 60_000, now))
        assertTrue(isAlertStale(MatchAlertType.MATCH_END, now - 7 * 60 * 60_000, now))
        // FCM reports 0 when it has no send time. Unknown is not old.
        assertFalse(isAlertStale(MatchAlertType.WICKET, 0, now))
    }
}

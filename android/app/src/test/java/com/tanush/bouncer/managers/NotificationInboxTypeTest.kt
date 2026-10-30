package com.tanush.bouncer.managers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [NotificationInboxManager.inferType] against the substring bug it shipped with.
 *
 * The classifier used `lower.contains("out")`, so "South Africa" — s-OUT-h — was a wicket. Because
 * the wicket branch is checked first it also shadowed six/four/start/end for every notification
 * mentioning South Africa, a knockout stage, or an outstanding spell. Only `inferType` is
 * exercised here: it is pure, so the manager needs no Context.
 */
class NotificationInboxTypeTest {

    @Test
    fun `team names containing out are not wickets`() {
        assertEquals("update", NotificationInboxManager.inferType("South Africa 245/4"))
        assertEquals("update", NotificationInboxManager.inferType("Knockout stage begins"))
        assertEquals("update", NotificationInboxManager.inferType("Outstanding spell from Bumrah"))
        assertEquals("update", NotificationInboxManager.inferType("About to resume after rain"))
    }

    @Test
    fun `a shadowed type is reachable once the team name stops matching`() {
        // Each of these previously returned "wicket" because the title contains "South".
        assertEquals("six", NotificationInboxManager.inferType("South Africa: SIX!"))
        assertEquals("four", NotificationInboxManager.inferType("South Africa: four through covers"))
        assertEquals("match_start", NotificationInboxManager.inferType("South Africa v India: start"))
        assertEquals("match_end", NotificationInboxManager.inferType("South Africa v India: result"))
    }

    @Test
    fun `real wickets still classify as wickets`() {
        assertEquals("wicket", NotificationInboxManager.inferType("WICKET! Kohli b Starc 42"))
        assertEquals("wicket", NotificationInboxManager.inferType("Rohit is out for 88"))
    }

    @Test
    fun `remaining types classify on their own words`() {
        assertEquals("six", NotificationInboxManager.inferType("Maxwell launches a six"))
        assertEquals("four", NotificationInboxManager.inferType("Boundary through midwicket"))
        assertEquals("milestone", NotificationInboxManager.inferType("Century for Root"))
        assertEquals("update", NotificationInboxManager.inferType("Rain delay at Lord's"))
    }
}

package com.tanush.bouncer.utils

import com.tanush.bouncer.models.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wicket detection is the highest-consequence classifier in the app: a WICKET here fires a
 * user-facing "Wicket!" push notification via LiveCommentaryManager.dispatchKeyEvent.
 *
 * Every case in [ordinary prose is not a wicket] previously produced one.
 */
class WicketDetectionTest {

    private fun isWicket(line: String) =
        TimelineEventExtractor.detectBallType(line) == TimelineEventType.WICKET

    // ── The false positives the exclusion-list approach could not catch ──

    @Test
    fun `ordinary prose is not a wicket`() {
        listOf(
            // REGRESSION: bare \bBOWLED\b matched the verb in ordinary description.
            "18.2 Bumrah to Root, bowled a beauty, no run",
            "4.1 Starc bowled a peach there, beaten all ends up",
            "9.3 bowled it full and straight, driven for one",
            // REGRESSION: bare \bCATCH\b matched a chance that was not taken.
            "12.4 a good catch attempt, dropped at slip",
            "7.2 tough catch chance goes down at deep midwicket",
            "15.1 what a catch that would have been!",
            // Positional phrases the old code needed an explicit exclusion list for.
            "4.2 FOUR! Swept to the MID-WICKET boundary",
            "7.1 Coming over the wicket to the right-hander",
            "3.3 Round the wicket now, angling in",
        ).forEach { assertFalse("must not be a wicket: \"$it\"", isWicket(it)) }
    }

    @Test
    fun `a dropped catch is not a wicket even when announced`() {
        assertFalse(isWicket("11.2 CAUGHT! ... no, dropped! Put down at mid-off"))
    }

    @Test
    fun `a dismissal off a no-ball is not a wicket`() {
        assertFalse(isWicket("6.4 BOWLED! But it's a no ball, he survives"))
        assertFalse(isWicket("6.4 OUT! Free hit though, so he stays"))
    }

    // ── Real dismissals must still fire ──

    @Test
    fun `announced dismissals are wickets`() {
        listOf(
            "10.3 WICKET! Kohli is caught at slip",
            "15.2 BOWLED! Clean bowled through the gate",
            "8.4 LBW! Trapped in front of the stumps",
            "12.1 RUN OUT! Direct hit from mid-on",
            "7.5 STUMPED! Quick work behind the stumps",
            "3.2 OUT! Edged and taken by the keeper",
            "8.2 WICKET! Bowled him",
            "19.4 GONE! Top edge and taken",
            "5.5 TIMBER! Off stump cartwheels",
        ).forEach { assertTrue("must be a wicket: \"$it\"", isWicket(it)) }
    }

    @Test
    fun `unambiguous dismissal terms are wickets without emphasis`() {
        listOf(
            "14.2 Kohli lbw b Anderson for 45",
            "6.3 Rohit is out caught behind",
            "11.1 run out at the non-striker's end",
            "9.6 stumped by the keeper, gone",
            "2.4 hit wicket, he's dislodged the bail",
            "17.3 caught and bowled, superb reflexes",
        ).forEach { assertTrue("must be a wicket: \"$it\"", isWicket(it)) }
    }

    @Test
    fun `scorecard dismissal notation is a wicket`() {
        assertTrue(isWicket("Kohli c Rahul b Bumrah 45"))
        assertTrue(isWicket("Pant st Carey b Lyon 12"))
    }

    // ── Milestones ──

    @Test
    fun `a bare score is not a milestone`() {
        // REGRESSION: \b(50|100|150|200|300)\b matched the number anywhere in the line.
        listOf(
            "150/3 in 20 overs",
            "England 100 for 2 at the break",
            "The partnership is worth 50 off 30 balls",
            "Bowled at 140 kph",
        ).forEach {
            assertFalse(
                "must not be a milestone: \"$it\"",
                TimelineEventExtractor.detectBallType(it) == TimelineEventType.MILESTONE,
            )
        }
    }

    @Test
    fun `worded milestones still fire`() {
        listOf(
            "That's his century! What an innings",
            "Reaches his fifty off 32 balls",
            "A well-deserved half-century for the opener",
            "That's a hat-trick! Three in three!",
            "He has a fifer! Five wickets in the innings",
        ).forEach {
            assertEquals(
                "must be a milestone: \"$it\"",
                TimelineEventType.MILESTONE,
                TimelineEventExtractor.detectBallType(it),
            )
        }
    }

    // ── Precedence ──

    @Test
    fun `a wicket outranks a boundary mentioned on the same line`() {
        assertEquals(
            TimelineEventType.WICKET,
            TimelineEventExtractor.detectBallType("8.4 WICKET! Caught at the FOUR boundary rope"),
        )
    }

    @Test
    fun `extract emits no wicket event for descriptive prose`() {
        val events = TimelineEventExtractor.extract(
            listOf(
                "18.2 Bumrah to Root, bowled a beauty, no run",
                "12.4 a good catch attempt, dropped at slip",
                "150/3 in 20 overs",
            ),
        )
        assertTrue("expected no events, got $events", events.none { it.type == TimelineEventType.WICKET })
        assertTrue("expected no milestone, got $events", events.none { it.type == TimelineEventType.MILESTONE })
    }
}

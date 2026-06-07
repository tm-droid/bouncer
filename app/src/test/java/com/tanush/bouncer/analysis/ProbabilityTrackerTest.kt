package com.tanush.bouncer.analysis

import android.content.Context
import android.content.SharedPreferences
import com.tanush.bouncer.models.ProbabilityEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

class ProbabilityTrackerTest {

    private lateinit var tracker: ProbabilityTracker

    private fun makeEvent(
        ball: Int,
        impact: Double,
        eventType: ProbabilityEvent.EventType = ProbabilityEvent.EventType.OTHER,
    ) = ProbabilityEvent(
        timestamp = Date(),
        ball = ball,
        over = ball / 6,
        probability = 50.0,
        eventType = eventType,
        description = "",
        impact = impact,
    )

    @Before
    fun setUp() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val context = mockk<Context>(relaxed = true) {
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        val calculator = mockk<WinProbabilityCalculator>(relaxed = true)
        tracker = ProbabilityTracker("match1", calculator, context)
    }

    @Test
    fun `getPhaseAnalysis empty events returns zeros`() {
        val result = tracker.getPhaseAnalysis()
        assertTrue(result.contains("Power Play Impact: 0.0%"))
        assertTrue(result.contains("Middle Overs Impact: 0.0%"))
        assertTrue(result.contains("Death Overs Impact: 0.0%"))
    }

    @Test
    fun `getPhaseAnalysis sums impacts into correct phases`() {
        val events = tracker.javaClass.getDeclaredField("events").apply { isAccessible = true }
            .get(tracker) as MutableList<ProbabilityEvent>
        events += makeEvent(ball = 6,   impact = 2.0)
        events += makeEvent(ball = 36,  impact = 3.0)
        events += makeEvent(ball = 37,  impact = 1.5)
        events += makeEvent(ball = 120, impact = 0.5)
        events += makeEvent(ball = 240, impact = 4.0)
        events += makeEvent(ball = 250, impact = 1.0)

        val result = tracker.getPhaseAnalysis()
        assertTrue(result.contains("Power Play Impact: 5.0%"))
        assertTrue(result.contains("Middle Overs Impact: 2.0%"))
        assertTrue(result.contains("Death Overs Impact: 5.0%"))
    }

    @Test
    fun `getEventAnalysis empty events returns zeros`() {
        val result = tracker.getEventAnalysis()
        assertTrue(result.contains("Boundaries Impact: 0.0%"))
        assertTrue(result.contains("Wickets Impact: 0.0%"))
        assertTrue(result.contains("Dot Balls Impact: 0.0%"))
    }

    @Test
    fun `getEventAnalysis sums impacts by event type`() {
        val events = tracker.javaClass.getDeclaredField("events").apply { isAccessible = true }
            .get(tracker) as MutableList<ProbabilityEvent>
        events += makeEvent(ball = 1, impact = 5.0,  eventType = ProbabilityEvent.EventType.BOUNDARY)
        events += makeEvent(ball = 2, impact = 3.0,  eventType = ProbabilityEvent.EventType.BOUNDARY)
        events += makeEvent(ball = 3, impact = -8.0, eventType = ProbabilityEvent.EventType.WICKET)
        events += makeEvent(ball = 4, impact = -1.0, eventType = ProbabilityEvent.EventType.DOT_BALL)
        events += makeEvent(ball = 5, impact = -1.0, eventType = ProbabilityEvent.EventType.DOT_BALL)
        events += makeEvent(ball = 6, impact = 2.0,  eventType = ProbabilityEvent.EventType.OTHER)

        val result = tracker.getEventAnalysis()
        assertTrue(result.contains("Boundaries Impact: 8.0%"))
        assertTrue(result.contains("Wickets Impact: -8.0%"))
        assertTrue(result.contains("Dot Balls Impact: -2.0%"))
    }
}

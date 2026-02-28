package com.tanush.bouncer.analysis

import com.nhaarman.mockitokotlin2.*
import com.tanush.bouncer.models.ProbabilityEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import android.content.Context
import android.content.SharedPreferences
import java.util.Date

class ProbabilityTrackerTest {
    private lateinit var calculator: WinProbabilityCalculator
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setUp() {
        calculator = mock()
        context = mock()
        prefs = mock()
        editor = mock()

        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
    }

    @Test
    fun `trackEvent adds event and computes impact`() {
        // Given
        whenever(calculator.calculateWinProbability(any(), any(), any(), any(), any())).thenReturn(60.0)
        val tracker = ProbabilityTracker("match-1", calculator, context)

        val situation = TrackerMatchSituation(
            requiredRunRate = 6.0,
            oversRemaining = 10,
            wicketsInHand = 7,
            currentRunRate = 6.0,
            target = 100
        )

        // When
        tracker.trackEvent(ball = 12, over = 2, runs = 4, isWicket = false, wicketType = null, extras = 0, extraType = null, matchSituation = situation)

        // Then
        val events = tracker.getProbabilityGraph()
        assertEquals(1, events.size)
        val ev = events[0]
        // probability should be the mocked value
        assertEquals(60.0, ev.probability, 0.001)
        // impact sh        // impact sh        // im)
        assertEquals(10.0, ev.impact, 0.001)
    }
}

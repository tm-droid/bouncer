package com.tanush.bouncer.logic

import kotlin.math.max
import kotlin.math.min

/**
 * MatchSimulator - The "Magic" Engine (v1.0).
 * Heuristic probability simulation for "What-If" tactical scenarios.
 */
object MatchSimulator {

    data class SimResult(
        val newProbability: Float,
        val impactScore: Float, // -1.0 to 1.0
        val summary: String
    )

    /**
     * Simulates the impact of a specific event on the current match state.
     */
    fun simulateEvent(
        currentProb: Float, // 0.0 to 1.0 (Team 1)
        event: SimulatedEvent,
        wicketsDown: Int,
        target: Int?,
        oversRemaining: Float
    ): SimResult {
        return when (event) {
            SimulatedEvent.WICKET -> {
                val impact = 0.12f + (10 - wicketsDown) * 0.01f
                val nextProb = max(0.05f, currentProb - impact)
                SimResult(
                    newProbability = nextProb,
                    impactScore = -impact,
                    summary = "Critical blow. Win probability dropped by ${(impact * 100).toInt()}%."
                )
            }
            SimulatedEvent.SIX -> {
                val impact = 0.08f + (oversRemaining / 20f) * 0.02f
                val nextProb = min(0.95f, currentProb + impact)
                SimResult(
                    newProbability = nextProb,
                    impactScore = impact,
                    summary = "Massive momentum shift. Probability surged by ${(impact * 100).toInt()}%."
                )
            }
            SimulatedEvent.DOT -> {
                val impact = 0.02f
                val nextProb = max(0.05f, currentProb - impact)
                SimResult(
                    newProbability = nextProb,
                    impactScore = -impact,
                    summary = "Pressure building. Dot ball impact: -${(impact * 100).toInt()}%."
                )
            }
        }
    }

    enum class SimulatedEvent {
        WICKET, SIX, DOT
    }
}

package com.tanush.bouncer.models

import java.util.Date

data class BallPrediction(
    val ballIndex: Int,
    val type: String, // "Wicket", "Boundary", "Dot", etc.
    val probability: Float,
    val description: String
)

data class ProbabilityEvent(
    val timestamp: Date,
    val ball: Int,
    val over: Int,
    val probability: Double,
    val eventType: EventType,
    val description: String,
    val impact: Double
) {
    enum class EventType {
        WICKET, BOUNDARY, DOT_BALL, POWER_PLAY, DEATH_OVERS, MILESTONE, OTHER
    }
}

package com.tanush.bouncer.models

enum class TimelineEventType {
    WICKET, FOUR, SIX, MILESTONE, OVER_END, POWERPLAY, DRINKS, INNINGS_BREAK, OTHER
}

data class TimelineEvent(
    val over: Float,
    val type: TimelineEventType,
    val title: String,
    val description: String,
    val team: String = "",
)

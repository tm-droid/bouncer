package com.tanush.bouncer.models

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable

enum class TimelineEventType {
    WICKET, FOUR, SIX, MILESTONE, OVER_END, POWERPLAY, DRINKS, INNINGS_BREAK, OTHER
}

@Serializable
@Immutable
data class TimelineEvent(
    val over: Float,
    val type: TimelineEventType,
    val title: String,
    val description: String,
    val team: String = "",
)

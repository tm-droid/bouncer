package com.tanush.bouncer.models

import androidx.compose.runtime.Immutable

@Immutable
data class BallDelivery(
    val overNumber: Float,
    val bowlerName: String,
    val batsmanName: String,
    val runs: Int,
    val isWicket: Boolean,
    val isBoundary: Boolean = false,
    val isSix: Boolean = false,
    val isExtra: Boolean = false,
    val extraType: String? = null,
    val commentary: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

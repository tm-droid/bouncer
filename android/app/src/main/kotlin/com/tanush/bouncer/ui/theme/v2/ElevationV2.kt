package com.tanush.bouncer.ui.theme.v2

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bouncer V2 elevation levels.
 * In dark mode, elevation is rendered via tonal overlay rather than shadow.
 */

@Immutable
data class BouncerElevationV2(
    /** 0dp — flat surface, no shadow */
    val none: Dp = 0.dp,
    /** 1dp — subtle lift (cards in lists) */
    val low: Dp = 1.dp,
    /** 2dp — default card elevation */
    val card: Dp = 2.dp,
    /** 4dp — elevated card (hero cards, active match) */
    val medium: Dp = 4.dp,
    /** 8dp — prominent (modals, popovers) */
    val high: Dp = 8.dp,
    /** 16dp — overlay (bottom sheets, dialogs) */
    val overlay: Dp = 16.dp,
)

val LocalBouncerElevationV2 = staticCompositionLocalOf { BouncerElevationV2() }

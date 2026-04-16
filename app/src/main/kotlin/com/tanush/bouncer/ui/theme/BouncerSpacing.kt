package com.tanush.bouncer.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Core spacing/elevation/radius tokens aligned to a strict 4dp grid.
 */
object BouncerTokenScales {
    val spacing = BouncerSpacing()
    val radius = BouncerRadius()
    val elevation = BouncerElevation()
}

@Immutable
data class BouncerSpacing(
    /** 2dp — Micro spacing for tight clusters (legacy compatibility). */
    val micro: Dp = 2.dp,
    /** 4dp — Extra small spacing. */
    val xs: Dp = 4.dp,
    /** 8dp — Small spacing. */
    val sm: Dp = 8.dp,
    /** 16dp — Medium spacing. */
    val md: Dp = 16.dp,
    /** 24dp — Large spacing. */
    val lg: Dp = 24.dp,
    /** 32dp — Extra large spacing. */
    val xl: Dp = 32.dp,
    /** 48dp — Double extra large spacing. */
    val xxl: Dp = 48.dp,
    /** 64dp — Large section spacing. */
    val xxxl: Dp = 64.dp,
    /** 80dp — Legacy jumbo spacing. */
    val jumbo: Dp = 80.dp,
    /** 96dp — Legacy mega spacing. */
    val mega: Dp = 96.dp,
    /** 128dp — Legacy ultra spacing. */
    val ultra: Dp = 128.dp
) {
    /** Legacy alias for micro spacing. */
    val xxs: Dp
        get() = micro

    /** Returns spacing as a 4dp-grid multiplier. */
    fun gridUnit(multiplier: Int): Dp = (4 * multiplier).dp
}

@Immutable
data class BouncerRadius(
    /** 4dp — Extra small radius. */
    val xs: Dp = 4.dp,
    /** 8dp — Small radius. */
    val sm: Dp = 8.dp,
    /** 12dp — Medium radius. */
    val md: Dp = 12.dp,
    /** 16dp — Large radius. */
    val lg: Dp = 16.dp,
    /** 20dp — XL radius. */
    val xl: Dp = 20.dp,
    /** 24dp — XXL radius / sheet corners. */
    val xxl: Dp = 24.dp,
    /** 100dp — Fully rounded capsule shape. */
    val pill: Dp = 100.dp,
    /** 20dp — Standard card corners. */
    val card: Dp = 20.dp,
    /** 28dp — Hero card corners. */
    val hero: Dp = 28.dp,
    /** 24dp — Bottom sheet corners. */
    val sheet: Dp = 24.dp
)

@Immutable
data class BouncerElevation(
    /** 0dp — Base surface. */
    val none: Dp = 0.dp,
    /** 2dp — Subtle elevated surface. */
    val sm: Dp = 2.dp,
    /** 4dp — Standard elevated surface. */
    val md: Dp = 4.dp,
    /** 8dp — Prominent elevated surface. */
    val lg: Dp = 8.dp,
    /** 12dp — Modal elevation. */
    val xl: Dp = 12.dp,
    /** 16dp — Overlay / max tier elevation. */
    val xxl: Dp = 16.dp
) {
    // Legacy aliases preserved for compatibility
    val level1: Dp
        get() = sm
    val level2: Dp
        get() = md
    val level3: Dp
        get() = lg
    val level4: Dp
        get() = xl
    val level5: Dp
        get() = xxl
    val modal: Dp
        get() = xl
    val overlay: Dp
        get() = xxl
}

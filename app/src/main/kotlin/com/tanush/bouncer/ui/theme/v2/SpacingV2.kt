package com.tanush.bouncer.ui.theme.v2

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bouncer V2 spacing tokens — strict 4dp grid with a few practical exceptions.
 */

@Immutable
data class BouncerSpacingV2(
    /** 2dp — micro gap (icon-to-text tight coupling) */
    val xxs: Dp = 2.dp,
    /** 4dp — extra small */
    val xs: Dp = 4.dp,
    /** 8dp — small (card inner padding, between badge elements) */
    val sm: Dp = 8.dp,
    /** 12dp — medium-small (between text blocks) */
    val md: Dp = 12.dp,
    /** 16dp — medium (standard card padding, list item spacing) */
    val lg: Dp = 16.dp,
    /** 24dp — large (section spacing) */
    val xl: Dp = 24.dp,
    /** 32dp — extra large (screen padding top, between major sections) */
    val xxl: Dp = 32.dp,
    /** 48dp — hero spacing (above/below hero content) */
    val xxxl: Dp = 48.dp,

    /**
     * 14dp — vertical padding inside one list row (match, player, series).
     *
     * A scores app is judged on how many scores fit on a screen: at the pre-August geometry (16dp
     * margin + 4dp gap + 16dp card padding) Home showed ~2.5 matches, against 4–5 in Cricbuzz and
     * Cricinfo. 10dp hit five but read as cramped — rows ran together because a divider, not a gap,
     * is all that separates them — so this trades the fifth match for air around the fourth.
     */
    val rowVertical: Dp = 14.dp,
    /** 16dp — between sections of one screen. Was [xl]; 24dp reads as a gap, not a break. */
    val sectionGap: Dp = 16.dp,
)

val LocalBouncerSpacingV2 = staticCompositionLocalOf { BouncerSpacingV2() }

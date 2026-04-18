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
)

val LocalBouncerSpacingV2 = staticCompositionLocalOf { BouncerSpacingV2() }

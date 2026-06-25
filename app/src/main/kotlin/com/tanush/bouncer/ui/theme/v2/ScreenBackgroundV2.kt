package com.tanush.bouncer.ui.theme.v2

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/**
 * Subtle full-screen background gradient built from V2 tokens.
 *
 * Top fades from [BouncerColorsV2.surface] down to [BouncerColorsV2.background] to give
 * flat list screens a touch of depth without competing with card content. Adapts to
 * light/dark automatically since it reads the active theme colors.
 *
 * Apply to a full-size container (e.g. a `Box(Modifier.fillMaxSize())`) behind the
 * scrollable content, with the surrounding `Scaffold` using a transparent container color.
 */
@Composable
fun Modifier.bouncerScreenBackground(): Modifier {
    val colors = BouncerThemeV2.colors
    return this.background(
        Brush.verticalGradient(
            colors = listOf(colors.surface, colors.background),
        ),
    )
}

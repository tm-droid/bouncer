package com.tanush.bouncer.ui.theme.v2

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The single flat screen background, built from V2 tokens.
 *
 * This used to be a two-stop `Brush.verticalGradient(surface, background)` justified as "a touch
 * of depth". Under a full-bleed scrolling list it is invisible, and a faint top-to-bottom wash
 * behind everything is one of the clearest tells of generated UI. Depth on these screens comes
 * from dividers and type weight instead.
 *
 * Apply to a full-size container (e.g. a `Box(Modifier.fillMaxSize())`) behind the
 * scrollable content, with the surrounding `Scaffold` using a transparent container color.
 */
@Composable
fun Modifier.bouncerScreenBackground(): Modifier = this.background(BouncerThemeV2.colors.background)

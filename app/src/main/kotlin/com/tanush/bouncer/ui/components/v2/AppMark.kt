package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R

/**
 * The app's own mark — the launcher icon's two layers, as the home screen draws them.
 *
 * The foreground is a black mark on a transparent canvas, so it needs its background layer under it
 * or it reads as a black blot in dark mode. Adaptive-icon layers are 108dp with the visible 72dp in
 * the middle, so both are drawn at 1.5x the tile and clipped to it. This replaces the stock Material
 * cricket glyph About and Update used for a logo.
 */
@Composable
fun AppMark(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(size * CORNER_FRACTION)),
        contentAlignment = Alignment.Center,
    ) {
        val layer = Modifier.requiredSize(size * ADAPTIVE_SCALE)
        Image(
            painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = layer,
            contentScale = ContentScale.Crop,
        )
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = layer,
            contentScale = ContentScale.Crop,
        )
    }
}

private const val ADAPTIVE_SCALE = 1.5f
private const val CORNER_FRACTION = 0.24f

package com.tanush.bouncer.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Applies a premium etched vertical gradient to text.
 * Colors: #FFFFFF (0%) -> #D4AF37 (60%) -> #B08D26 (100%)
 */
fun Modifier.etchedText(
    useGold: Boolean = true
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithCache {
        val brush = if (useGold) {
            Brush.verticalGradient(
                0.0f to Color.White,
                0.6f to Color(0xFFD4AF37),
                1.0f to Color(0xFFB08D26),
                startY = 0f,
                endY = size.height
            )
        } else {
            Brush.verticalGradient(
                0.0f to Color.White,
                1.0f to Color.White.copy(alpha = 0.6f),
                startY = 0f,
                endY = size.height
            )
        }
        onDrawWithContent {
            drawContent()
            drawRect(brush, blendMode = BlendMode.SrcIn)
        }
    }

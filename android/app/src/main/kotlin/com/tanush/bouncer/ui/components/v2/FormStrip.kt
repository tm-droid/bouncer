package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.screens.FormResult
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Recent results as a row of squares, newest first.
 *
 * Neutral by design: a win is a solid square, a loss an outline, anything else a soft fill. Colouring
 * them green and red made every form guide read as a traffic light and spent the live red on results.
 */
@Composable
fun FormStrip(results: List<FormResult>, modifier: Modifier = Modifier, cell: Dp = 20.dp) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val shape = BouncerThemeV2.shapes.extraSmall
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        results.forEach { result ->
            val fill = when (result) {
                FormResult.WON -> colors.onSurface
                FormResult.LOST -> colors.surface
                else -> colors.surfaceVariant
            }
            val ink = if (result == FormResult.WON) colors.surface else colors.onSurface
            Box(
                modifier = Modifier
                    .size(cell)
                    .clip(shape)
                    .background(fill)
                    .then(if (result == FormResult.LOST) Modifier.border(1.dp, colors.border, shape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = result.letter,
                    style = typography.numericSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    maxLines = 1,
                )
            }
        }
    }
}

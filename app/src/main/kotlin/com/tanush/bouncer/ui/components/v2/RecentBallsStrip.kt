package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.contrastRatio
import com.tanush.bouncer.ui.theme.v2.White

private val BALL_SIZE = 22.dp

/**
 * Filled pills for the deliveries of an over, in Cricbuzz's own notation.
 *
 * The glyph is rendered whole. It used to be `ball.take(2)`, which turned `Wd3` into `Wd` — and a
 * wide worth three is a different ball from a wide. A one-character glyph still reads as a circle;
 * a longer one grows sideways rather than losing its runs.
 */
@Composable
fun RecentBallsStripV2(
    balls: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        balls.takeLast(12).forEach { ball ->
            val (bg, text) = ballStyle(ball, colors)
            Box(
                modifier = Modifier
                    // A minimum, not a fixed height: at a large font scale the 16sp line no longer
                    // fits 22dp, and a clipped fixed box cut the glyph top and bottom.
                    .defaultMinSize(minWidth = BALL_SIZE, minHeight = BALL_SIZE)
                    .clip(RoundedCornerShape(BALL_SIZE / 2))
                    .background(bg)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ball.trim(),
                    style = typography.labelSmall,
                    color = text,
                )
            }
        }
    }
}

private fun ballStyle(ball: String, colors: BouncerColorsToken): Pair<Color, Color> {
    val cricket = colors.cricket
    return when (ballGlyphKindOf(ball)) {
        BallGlyphKind.WICKET -> cricket.wicket to ballChipInk(cricket.wicket)
        BallGlyphKind.FOUR -> cricket.four to ballChipInk(cricket.four)
        BallGlyphKind.SIX -> cricket.six to ballChipInk(cricket.six)
        BallGlyphKind.EXTRA -> cricket.wide to ballChipInk(cricket.wide)
        BallGlyphKind.DOT -> colors.textSecondary.copy(alpha = 0.3f) to colors.onSurface
        BallGlyphKind.RUNS -> colors.primary.copy(alpha = 0.2f) to colors.onSurface
    }
}

/**
 * The glyph colour for an event chip: whichever of white and black reads better on
 * [fill]. It was always white, on a fill drawn at 85% alpha — 2.3:1 on the dark palette's green
 * and amber, and the alpha took the light palette's fills under 4.5:1 as well. The fill is solid
 * now, so what the contrast test measures is what is drawn.
 */
internal fun ballChipInk(fill: Color): Color =
    if (White.contrastRatio(fill) >= Color.Black.contrastRatio(fill)) White else Color.Black

// Type alias for readability in helper
private typealias BouncerColorsToken = com.tanush.bouncer.ui.theme.v2.BouncerColorsV2

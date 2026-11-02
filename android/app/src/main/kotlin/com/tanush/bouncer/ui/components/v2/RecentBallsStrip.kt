package com.tanush.bouncer.ui.components.v2

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.contrastRatio
import com.tanush.bouncer.ui.theme.v2.White

private val BALL_SIZE = 22.dp
private const val MAX_BALLS = 12
private const val ARRIVAL_START_SCALE = 0.6f

/**
 * True when [current] is [previous] plus a delivery: appended, slid along a full strip, or a fresh
 * over after a longer one. Opening composition, a re-send of the same list and a correction to the
 * last ball are not arrivals, so the scale-in plays once per delivery.
 */
internal fun ballArrived(previous: List<String>, current: List<String>): Boolean {
    val before = previous.takeLast(MAX_BALLS)
    val now = current.takeLast(MAX_BALLS)
    return when {
        now.isEmpty() || now == before -> false
        now.size > before.size -> now.take(before.size) == before
        now.size == before.size -> now.dropLast(1) == before.drop(1)
        else -> now.size == 1
    }
}

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
    val shown = balls.takeLast(MAX_BALLS)

    var arrivals by remember { mutableIntStateOf(0) }
    val previous = remember { arrayOf(balls) }
    LaunchedEffect(balls) {
        if (ballArrived(previous[0], balls)) arrivals++
        previous[0] = balls
    }
    val newestScale = remember { Animatable(1f) }
    LaunchedEffect(arrivals) {
        if (arrivals > 0 && !BouncerMotion.reducedMotionEnabled) {
            newestScale.snapTo(ARRIVAL_START_SCALE)
            newestScale.animateTo(1f, BouncerMotion.Springs.noBounce())
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEachIndexed { index, ball ->
            val (bg, text) = ballStyle(ball, colors)
            Box(
                modifier = Modifier
                    .then(
                        if (index == shown.lastIndex) {
                            Modifier.graphicsLayer {
                                scaleX = newestScale.value
                                scaleY = newestScale.value
                            }
                        } else {
                            Modifier
                        },
                    )
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
                    style = typography.numericSmall,
                    fontWeight = FontWeight.SemiBold,
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
        // Gold is the interactive colour; an ordinary run is not a control.
        BallGlyphKind.RUNS -> colors.surfaceVariant to colors.onSurface
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

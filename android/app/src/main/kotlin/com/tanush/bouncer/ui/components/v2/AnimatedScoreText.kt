package com.tanush.bouncer.ui.components.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tanush.bouncer.ui.theme.BouncerMotion

/** [stablePrefix] stays put; [oldSuffix] rolls out and [newSuffix] rolls in. */
data class ScoreRollSplit(val stablePrefix: String, val oldSuffix: String, val newSuffix: String)

/** "187/4" -> "191/4" keeps "1" and rolls "87/4" out for "91/4". Equal strings roll nothing. */
fun scoreRollSplit(old: String, new: String): ScoreRollSplit {
    if (old == new) return ScoreRollSplit(new, "", "")
    var n = 0
    val max = minOf(old.length, new.length)
    while (n < max && old[n] == new[n]) n++
    return ScoreRollSplit(new.substring(0, n), old.substring(n), new.substring(n))
}

/**
 * Drop-in replacement for a score [Text]: only the characters that changed roll (slide + fade,
 * at most 200 ms, no counting). With the system animator scale at 0 it snaps.
 *
 * Set [describedByParent] when an enclosing [scoreSemantics] container speaks the full sentence;
 * the digits then add no semantics of their own, so TalkBack reads the score once.
 */
@Composable
fun AnimatedScoreText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    describedByParent: Boolean = false,
) {
    // The split is fixed per text so an unrelated recomposition cannot turn the prefix into the
    // whole string and re-roll nothing.
    val last = remember { arrayOf(text) }
    val split = remember(text) { scoreRollSplit(last[0], text).also { last[0] = text } }
    val semanticsModifier = if (describedByParent) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = text
        }
    }

    val prefixLength = split.stablePrefix.length
    // The stable prefix is drawn once, outside the transition. Each rolling child carries an
    // invisible copy of it so both children keep the same width and baseline.
    Box(modifier = modifier.then(semanticsModifier)) {
        ScoreSegment(split.stablePrefix, style, color, fontWeight)
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                if (BouncerMotion.reducedMotionEnabled) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (
                        slideInVertically(tween(BouncerMotion.Duration.Medium)) { it / 2 } +
                            fadeIn(tween(BouncerMotion.Duration.Medium))
                        ) togetherWith (
                        slideOutVertically(tween(BouncerMotion.Duration.Brisk)) { -it / 2 } +
                            fadeOut(tween(BouncerMotion.Duration.Brisk))
                        )
                }
            },
            label = "animatedScoreText",
        ) { target ->
            Row {
                ScoreSegment(target.take(prefixLength), style, Color.Transparent, fontWeight)
                ScoreSegment(target.drop(prefixLength), style, color, fontWeight)
            }
        }
    }
}

@Composable
private fun ScoreSegment(text: String, style: TextStyle, color: Color, fontWeight: FontWeight?) {
    Text(
        text = text,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
}

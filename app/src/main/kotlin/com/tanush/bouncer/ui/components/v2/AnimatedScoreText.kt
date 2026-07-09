package com.tanush.bouncer.ui.components.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tanush.bouncer.ui.theme.BouncerMotion

/**
 * Drop-in replacement for a score [Text] that animates value changes
 * (slide-up + fade) instead of snapping. Falls back to a plain crossfade
 * when reduced motion is enabled. Only recomposes/animates when [text]
 * actually changes, so it is safe on live-updating list items.
 */
@Composable
fun AnimatedScoreText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            if (BouncerMotion.reducedMotionEnabled) {
                fadeIn(BouncerMotion.Specs.ReducedFadeIn) togetherWith
                    fadeOut(BouncerMotion.Specs.ReducedFadeOut)
            } else {
                (
                    slideInVertically(BouncerMotion.Specs.Slide) { it / 2 } +
                        fadeIn(BouncerMotion.Specs.FadeIn)
                    ) togetherWith (
                    slideOutVertically(BouncerMotion.Specs.Slide) { -it / 2 } +
                        fadeOut(BouncerMotion.Specs.FadeOut)
                    )
            }
        },
        label = "animatedScoreText",
        // Polite live region so TalkBack announces score changes without
        // interrupting; description sits on the stable parent, not the
        // animating child, so it isn't re-registered mid-transition.
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = text
        },
    ) { targetText ->
        Text(
            text = targetText,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}

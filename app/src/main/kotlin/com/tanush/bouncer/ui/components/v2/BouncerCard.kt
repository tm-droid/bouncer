package com.tanush.bouncer.ui.components.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

private const val PRESSED_SCALE = 0.98f

/**
 * Base grouping container for Bouncer V2: 12dp corners, `surface` fill, hairline border, **no
 * shadow**. Clickable cards get a subtle press-scale (disabled when reduced motion is on).
 *
 * A card here means "these things belong together and are separate from what is around them" —
 * nothing more. It is not a level of importance and not a way to add depth. The 2dp drop shadow
 * it used to carry made every section of every screen float by the same amount, which reads as a
 * dashboard of equal tiles rather than a page with a hierarchy; a hairline does the same
 * separating job without lifting anything.
 *
 * Do **not** use this for the items of a homogeneous list — those are rows on one surface with
 * dividers (see `MatchCardV2`). A card per row is the tell this design system is built to avoid.
 */
@Composable
fun BouncerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    pressScale: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val shapes = BouncerThemeV2.shapes
    val border = BorderStroke(1.dp, colors.divider)

    val longPressMod = if (onLongClick != null) {
        Modifier.pointerInput(onLongClick) {
            detectTapGestures(onLongPress = { onLongClick.invoke() })
        }
    } else {
        Modifier
    }

    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scaleEnabled = pressScale && !BouncerMotion.reducedMotionEnabled
        val scale by animateFloatAsState(
            targetValue = if (pressed && scaleEnabled) PRESSED_SCALE else 1f,
            animationSpec = BouncerMotion.Springs.snappy(),
            label = "cardPressScale",
        )
        Card(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(longPressMod),
            shape = shapes.medium,
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = border,
            content = content,
        )
    } else {
        Card(
            modifier = modifier.then(longPressMod),
            shape = shapes.medium,
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = border,
            content = content,
        )
    }
}

/**
 * A [BouncerCard] holding several rows of one kind, split by hairlines.
 *
 * This is the answer to "I have three of these to show": one container, three rows — not three
 * containers. Reach for it wherever a screen would otherwise emit a card per item.
 */
@Composable
fun <T> BouncerRowGroup(items: List<T>, row: @Composable (T) -> Unit) {
    BouncerCard {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = BouncerThemeV2.colors.divider)
            row(item)
        }
    }
}

package com.tanush.bouncer.ui.components.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

private const val PRESSED_SCALE = 0.98f

/**
 * Base elevated card for Bouncer V2. 12dp rounded corners, subtle shadow, surface bg.
 * Clickable cards get a subtle press-scale (disabled when reduced motion is on).
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
    val elevation = BouncerThemeV2.elevation

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
            elevation = CardDefaults.cardElevation(defaultElevation = elevation.card),
            content = content,
        )
    } else {
        Card(
            modifier = modifier.then(longPressMod),
            shape = shapes.medium,
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation.card),
            content = content,
        )
    }
}

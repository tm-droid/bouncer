package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Shared building blocks used by enhanced match components.
 */
@Composable
internal fun rememberPulse(
    initial: Float = 0.6f,
    target: Float = 1f,
    durationMillis: Int = 1000,
    label: String = "pulse"
): Float {
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = initial,
        targetValue = target,
        animationSpec = infiniteRepeatable(tween(durationMillis), RepeatMode.Reverse),
        label = label
    ).value
}

@Composable
internal fun SectionLabel(text: String, color: Color, fontSize: Int = 10, letterSpacing: Float = 1f) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = letterSpacing.sp
    )
}

internal fun teamShortLabel(acronym: String?, name: String?): String =
    acronym ?: name?.take(3)?.uppercase() ?: "TBD"

@Composable
internal fun LiveBadge() {
    val colors = BouncerDesignSystem.colors
    val pulse = rememberPulse(label = "liveBadge")

    Surface(
        color = colors.statusLive.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.statusLive.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer { alpha = pulse }
                    .background(colors.statusLive, CircleShape)
            )
            Text(
                text = "LIVE",
                color = colors.statusLive,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

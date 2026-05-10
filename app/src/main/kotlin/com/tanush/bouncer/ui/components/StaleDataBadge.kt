package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.accessibility.rememberReducedMotionEnabled
import com.tanush.bouncer.utils.FreshnessFormatter
import kotlinx.coroutines.delay

// ==========================================
// StaleDataBadge — Compact freshness indicator
// ==========================================

private val PillBackground = Color(0xFF2A2A2A)
private val ContentColor = Color.White.copy(alpha = 0.5f)
private val BorderColor = Color.White.copy(alpha = 0.1f)

@Composable
fun StaleDataBadge(
    staleAtMillis: Long,
    modifier: Modifier = Modifier
) {
    var relativeText by remember { mutableStateOf(FreshnessFormatter.format(staleAtMillis)) }

    // Refresh the label every 30 seconds
    LaunchedEffect(staleAtMillis) {
        while (true) {
            relativeText = FreshnessFormatter.format(staleAtMillis)
            delay(30_000L)
        }
    }

    val ageSeconds = (System.currentTimeMillis() - staleAtMillis) / 1000
    val isStale = ageSeconds > 60

    val reducedMotion = rememberReducedMotionEnabled()
    val pulseAlpha = if (isStale && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "stalePulse")
        val alpha by transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "stalePulseAlpha"
        )
        alpha
    } else {
        0.5f
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = PillBackground,
        border = BorderStroke(0.5.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = "Data age",
                tint = ContentColor,
                modifier = Modifier
                    .size(14.dp)
                    .alpha(pulseAlpha)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = relativeText,
                color = ContentColor,
                fontSize = 11.sp,
                modifier = Modifier.alpha(pulseAlpha)
            )
        }
    }
}

// ==========================================
// Previews
// ==========================================

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StaleDataBadgeRecentPreview() {
    StaleDataBadge(staleAtMillis = System.currentTimeMillis() - 10_000)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StaleDataBadgeOldPreview() {
    StaleDataBadge(staleAtMillis = System.currentTimeMillis() - 360_000)
}

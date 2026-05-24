package com.tanush.bouncer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.GlassSurface
import com.tanush.bouncer.ui.theme.GoldPrimary
import kotlinx.coroutines.delay

private const val MILLIS_PER_SECOND = 1000L
private const val MILLIS_PER_MINUTE = MILLIS_PER_SECOND * 60
private const val MILLIS_PER_HOUR = MILLIS_PER_MINUTE * 60
private const val MILLIS_PER_DAY = MILLIS_PER_HOUR * 24
private const val LAST_MINUTE_MILLIS = MILLIS_PER_MINUTE
private const val LAST_HOUR_MILLIS = MILLIS_PER_HOUR

/**
 * MatchCountdown animated timer for upcoming matches
 * Displays countdown to match start with animated digits
 */
@Composable
fun MatchCountdown(
    targetTimeMillis: Long,
    matchTitle: String = "",
    onMatchStart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    var remainingTime by remember { mutableLongStateOf(targetTimeMillis - System.currentTimeMillis()) }

    LaunchedEffect(targetTimeMillis) {
        while (remainingTime > 0) {
            delay(MILLIS_PER_SECOND)
            remainingTime = (targetTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
            if (remainingTime <= 0) {
                onMatchStart()
            }
        }
    }

    val pulse = rememberPulse(initial = 1f, target = 1.05f, durationMillis = 500, label = "countdown")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = GlassSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(
                    GoldPrimary.copy(alpha = 0.3f),
                    GoldPrimary.copy(alpha = 0.1f),
                    GoldPrimary.copy(alpha = 0.3f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (matchTitle.isNotEmpty()) {
                Text(
                    text = matchTitle,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "MATCH STARTS IN",
                color = colors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            CountdownUnitsRow(remainingTime = remainingTime, pulse = pulse)

            if (remainingTime < LAST_HOUR_MILLIS) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Starting Soon!",
                    color = colors.statusSuccess,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CountdownUnitsRow(remainingTime: Long, pulse: Float) {
    val days = (remainingTime / MILLIS_PER_DAY).toInt()
    val hours = ((remainingTime / MILLIS_PER_HOUR) % 24).toInt()
    val minutes = ((remainingTime / MILLIS_PER_MINUTE) % 60).toInt()
    val seconds = ((remainingTime / MILLIS_PER_SECOND) % 60).toInt()

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (days > 0) {
            CountdownUnit(value = days, label = "DAYS", pulse = pulse)
            CountdownSeparator()
        }

        CountdownUnit(
            value = hours,
            label = "HRS",
            pulse = if (days == 0) pulse else 1f
        )
        CountdownSeparator()

        CountdownUnit(
            value = minutes,
            label = "MIN",
            pulse = if (days == 0 && hours == 0) pulse else 1f
        )
        CountdownSeparator()

        CountdownUnit(
            value = seconds,
            label = "SEC",
            pulse = pulse,
            highlight = remainingTime < LAST_MINUTE_MILLIS
        )
    }
}

@Composable
private fun CountdownUnit(
    value: Int,
    label: String,
    pulse: Float = 1f,
    highlight: Boolean = false
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val tileBackground = if (highlight) hierarchy.secondary.primary.copy(alpha = 0.2f) else GlassSurface
    val tileBorder = if (highlight) hierarchy.secondary.primary.copy(alpha = 0.5f) else colors.glassBorder.copy(alpha = 0.3f)
    val valueColor = if (highlight) hierarchy.secondary.primary else colors.textPrimary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
                .background(tileBackground, RoundedCornerShape(12.dp))
                .border(1.dp, tileBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format("%02d", value),
                color = valueColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun CountdownSeparator() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val alpha = rememberPulse(initial = 0.3f, target = 1f, durationMillis = 500, label = "separator")

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 20.dp)
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(hierarchy.secondary.primary, CircleShape)
            )
        }
    }
}

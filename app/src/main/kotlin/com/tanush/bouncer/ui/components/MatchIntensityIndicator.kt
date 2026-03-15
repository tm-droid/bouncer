package com.tanush.bouncer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.accessibility.rememberReducedMotionEnabled
import kotlin.math.abs

// ==========================================
// MatchIntensityIndicator — Visual heat indicator for match cards
// ==========================================

enum class MatchIntensity {
    CALM,
    BUILDING,
    INTENSE,
    EXPLOSIVE
}

data class IntensityFactors(
    val requiredRunRate: Float = 0f,
    val currentRunRate: Float = 0f,
    val wicketsInLast5Overs: Int = 0,
    val oversRemaining: Float = 0f,
    val runDifferential: Int = 0
)

object IntensityCalculator {
    fun calculate(factors: IntensityFactors): MatchIntensity {
        val absDiff = abs(factors.runDifferential)

        // EXPLOSIVE checks
        if ((factors.oversRemaining <= 5f && absDiff <= 30) ||
            factors.requiredRunRate > 12f ||
            factors.wicketsInLast5Overs >= 3
        ) return MatchIntensity.EXPLOSIVE

        // INTENSE checks
        if ((factors.oversRemaining <= 15f && absDiff <= 50) ||
            factors.requiredRunRate > 8f ||
            factors.wicketsInLast5Overs >= 2
        ) return MatchIntensity.INTENSE

        // BUILDING checks
        if (factors.currentRunRate > 6f || factors.wicketsInLast5Overs >= 1) {
            return MatchIntensity.BUILDING
        }

        return MatchIntensity.CALM
    }
}

// Color constants
private val CalmColor = Color(0xFF333333)
private val BuildingColor = Color(0xFFD4AF37).copy(alpha = 0.4f)
private val IntenseColor = Color(0xFFD4AF37)
private val ExplosiveGold = Color(0xFFD4AF37)
private val ExplosiveAmber = Color(0xFFFF8F00)

private fun MatchIntensity.baseColor(): Color = when (this) {
    MatchIntensity.CALM -> CalmColor
    MatchIntensity.BUILDING -> BuildingColor
    MatchIntensity.INTENSE -> IntenseColor
    MatchIntensity.EXPLOSIVE -> ExplosiveGold
}

private fun MatchIntensity.label(): String = when (this) {
    MatchIntensity.CALM -> "Calm"
    MatchIntensity.BUILDING -> "Building"
    MatchIntensity.INTENSE -> "Intense"
    MatchIntensity.EXPLOSIVE -> "\uD83D\uDD25 Explosive"
}

@Composable
fun MatchIntensityIndicator(
    intensity: MatchIntensity,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.matchIntensityBorder(intensity))
}

@Composable
fun MatchIntensityBadge(
    intensity: MatchIntensity,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val dotColor by animateColorAsState(
        targetValue = intensity.baseColor(),
        animationSpec = tween(durationMillis = 500),
        label = "badgeDotColor"
    )

    // Pulsing scale for EXPLOSIVE dot
    val dotScale = if (intensity == MatchIntensity.EXPLOSIVE && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "explosivePulse")
        val scale by transition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        scale
    } else {
        1f
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(dotScale)
                .background(color = dotColor, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = intensity.label(),
            fontSize = 11.sp,
            color = dotColor.copy(alpha = 0.7f)
        )
    }
}

fun Modifier.matchIntensityBorder(intensity: MatchIntensity): Modifier = composed {
    val reducedMotion = rememberReducedMotionEnabled()
    val shape = RoundedCornerShape(12.dp)

    if (intensity == MatchIntensity.EXPLOSIVE && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "explosiveBorder")
        val fraction by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "explosiveFraction"
        )
        val animatedColor = lerp(ExplosiveGold, ExplosiveAmber, fraction)
        this.drawBehind {
            drawRoundRect(
                color = animatedColor,
                style = Stroke(width = 2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
            )
        }
    } else {
        val animatedColor by animateColorAsState(
            targetValue = intensity.baseColor(),
            animationSpec = tween(durationMillis = 500),
            label = "borderColor"
        )
        this.border(width = 2.dp, color = animatedColor, shape = shape)
    }
}

// ==========================================
// Previews
// ==========================================

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
private fun MatchIntensityBadgePreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MatchIntensity.entries.forEach { intensity ->
            MatchIntensityBadge(intensity = intensity)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
private fun MatchIntensityBorderPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MatchIntensity.entries.forEach { intensity ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchIntensityBorder(intensity)
                    .padding(16.dp)
            ) {
                Text(
                    text = "${intensity.name} border",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

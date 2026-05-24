package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.GlassSurface
import kotlin.math.abs

/**
 * TeamMomentumIndicator with animated arrows
 * Visualizes team momentum using animated directional indicators
 */
@Composable
fun TeamMomentumIndicator(
    team1Name: String,
    team2Name: String,
    momentumValue: Float,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val animatedMomentum by animateFloatAsState(
        targetValue = momentumValue,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "momentum"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "momentumArrows")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowPulse"
    )

    val momentumDescription = momentumDescriptionFor(animatedMomentum, team1Name, team2Name)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = momentumDescription },
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SectionLabel("MOMENTUM", colors.textMuted)
            Spacer(modifier = Modifier.height(12.dp))
            MomentumTeamLabels(team1Name, team2Name, animatedMomentum)
            Spacer(modifier = Modifier.height(8.dp))
            MomentumBar(animatedMomentum, arrowOffset)
            Spacer(modifier = Modifier.height(8.dp))
            MomentumLabel(animatedMomentum)
        }
    }
}

private fun momentumDescriptionFor(value: Float, team1Name: String, team2Name: String): String = when {
    value < -0.3f -> "$team1Name has strong momentum"
    value > 0.3f -> "$team2Name has strong momentum"
    else -> "Momentum is even between both teams"
}

@Composable
private fun MomentumTeamLabels(team1Name: String, team2Name: String, animatedMomentum: Float) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = team1Name.take(3).uppercase(),
            color = if (animatedMomentum < 0) hierarchy.secondary.primary else colors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = team2Name.take(3).uppercase(),
            color = if (animatedMomentum > 0) hierarchy.secondary.primary else colors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MomentumBar(animatedMomentum: Float, arrowOffset: Float) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(4.dp))
                .background(GlassSurface)
        )

        Box(
            modifier = Modifier
                .width(2.dp)
                .height(16.dp)
                .align(Alignment.Center)
                .background(colors.textMuted)
        )

        MomentumIndicatorBar(animatedMomentum)

        if (abs(animatedMomentum) > 0.2f) {
            MomentumArrows(animatedMomentum, arrowOffset)
        }
    }
}

@Composable
private fun BoxScope.MomentumIndicatorBar(animatedMomentum: Float) {
    val colors = BouncerDesignSystem.colors
    val indicatorOffset = (animatedMomentum + 1) / 2
    val gradientColors = if (animatedMomentum < 0) {
        listOf(colors.statusSuccess, colors.statusSuccess.copy(alpha = 0.5f))
    } else {
        listOf(colors.statusInfo.copy(alpha = 0.5f), colors.statusInfo)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.1f)
            .height(24.dp)
            .align(Alignment.CenterStart)
            .offset(x = (indicatorOffset * 0.9f * 300).dp)
            .background(
                brush = Brush.horizontalGradient(colors = gradientColors),
                shape = RoundedCornerShape(12.dp)
            )
    )
}

@Composable
private fun BoxScope.MomentumArrows(animatedMomentum: Float, arrowOffset: Float) {
    val colors = BouncerDesignSystem.colors
    val isLeft = animatedMomentum < 0
    val offsetX = (animatedMomentum * 60).dp + (if (isLeft) -arrowOffset else arrowOffset).dp
    val arrowCount = minOf(3, (abs(animatedMomentum) * 5).toInt().coerceAtLeast(1))
    val arrowIcon = if (isLeft) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight
    val arrowTint = if (isLeft) colors.statusSuccess else colors.statusInfo

    Row(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x = offsetX),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(arrowCount) {
            Icon(
                imageVector = arrowIcon,
                contentDescription = null,
                tint = arrowTint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun MomentumLabel(animatedMomentum: Float) {
    val colors = BouncerDesignSystem.colors
    val magnitude = abs(animatedMomentum)
    val labelText = momentumLabelText(magnitude)
    val labelColor = when {
        magnitude < 0.2f -> colors.textMuted
        animatedMomentum < 0 -> colors.statusSuccess
        else -> colors.statusInfo
    }

    Text(
        text = labelText,
        color = labelColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    )
}

private fun momentumLabelText(magnitude: Float): String = when {
    magnitude < 0.2f -> "BALANCED"
    magnitude < 0.5f -> "SLIGHT ADVANTAGE"
    magnitude < 0.75f -> "MOMENTUM SHIFT"
    else -> "DOMINANCE"
}

package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.rememberHapticFeedback
import kotlin.math.*
import com.tanush.bouncer.ui.theme.GoldPrimary

private data class FormQualitative(val label: String, val color: Color)

@Composable
private fun rememberFormQualitative(avgScore: Double): FormQualitative {
    val colors = BouncerDesignSystem.colors
    return when {
        avgScore >= 50 -> FormQualitative("EXCELLENT", StatusSuccess)
        avgScore >= 30 -> FormQualitative("GOOD", colors.statusInfo)
        avgScore >= 15 -> FormQualitative("AVERAGE", StatusWarning)
        else -> FormQualitative("POOR", StatusLive)
    }
}

@Composable
private fun formBarColor(score: Int): Color {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    return when {
        score >= 100 -> hierarchy.secondary.primary
        score >= 50 -> StatusSuccess
        score >= 30 -> colors.statusInfo
        score > 0 -> colors.textSecondary
        else -> StatusLive
    }
}

@Composable
private fun FormIndicatorHeader(recentScores: List<Int>) {
    val colors = BouncerDesignSystem.colors
    val avgScore = if (recentScores.isNotEmpty()) recentScores.average() else 0.0
    val qualitative = rememberFormQualitative(avgScore)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "RECENT FORM",
            color = colors.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )

        Text(
            text = qualitative.label,
            color = qualitative.color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun RowScope.FormBar(score: Int, maxScore: Int) {
    val heightFraction = (score.toFloat() / maxScore).coerceIn(0.1f, 1f)
    val barColor = formBarColor(score)

    val animatedHeight by animateFloatAsState(
        targetValue = heightFraction,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "formBar"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp * animatedHeight)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(barColor)
        )

        Text(
            text = score.toString(),
            color = barColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * FormIndicator - Runs in last 5 innings sparkline
 * Shows recent form with visual indicators
 */
@Composable
fun FormIndicator(
    recentScores: List<Int>,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true
) {
    val colors = BouncerDesignSystem.colors
    val maxScore = recentScores.maxOrNull()?.coerceAtLeast(50) ?: 50

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.glassSurface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (showLabels) {
                FormIndicatorHeader(recentScores = recentScores)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                recentScores.takeLast(5).forEach { score ->
                    FormBar(score = score, maxScore = maxScore)
                }
            }
        }
    }
}

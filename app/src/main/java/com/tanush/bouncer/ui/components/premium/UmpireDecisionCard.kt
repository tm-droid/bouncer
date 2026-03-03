package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed
import kotlin.math.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Umpire decision card showing on-field decisions
 *
 * TODO: Integration Requirements:
 * - Track umpire decisions from match feed
 * - Show decision type (out/not out, wide, no ball)
 * - Display DRS review status and outcome
 * - Calculate umpire accuracy for the match
 * - Show historical umpire decision patterns
 * - Add decision timeline visualization
 * - Track controversy decisions
 * - Navigate to decision replay video
 * - Compare umpire accuracy ratings
 * - Cache decision data for match analysis
 */

data class UmpireDecisionData(
    val decisionType: DecisionType,
    val originalDecision: String,
    val finalDecision: String,
    val umpireName: String,
    val isReviewed: Boolean,
    val reviewOutcome: String?,
    val timeStamp: String,
    val description: String,
)

enum class DecisionType {
    LBW, CAUGHT, BOWLED, RUN_OUT, STUMPED, HIT_WICKET, NO_BALL, WIDE, BOUNDARY_CHECK, OBSTRUCTING
}

@Composable
fun UmpireDecisionCard(
    decision: UmpireDecisionData,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "umpire")
    val flashPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flash",
    )

    LaunchedEffect(decision) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        )
    }

    val decisionIcon = when (decision.decisionType) {
        DecisionType.LBW -> "LBW"
        DecisionType.CAUGHT -> "CT"
        DecisionType.BOWLED -> "B"
        DecisionType.RUN_OUT -> "RO"
        DecisionType.STUMPED -> "ST"
        DecisionType.HIT_WICKET -> "HW"
        DecisionType.NO_BALL -> "NB"
        DecisionType.WIDE -> "WD"
        DecisionType.BOUNDARY_CHECK -> "BC"
        DecisionType.OBSTRUCTING -> "OB"
    }

    val isOutDecision = decision.finalDecision.uppercase() == "OUT"
    val resultColor = if (isOutDecision) colors.statusLive else colors.statusSuccess

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        resultColor.copy(alpha = flashPulse),
                                        resultColor.copy(alpha = 0.6f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Gavel,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "UMPIRE DECISION",
                            color = colors.goldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = decision.decisionType.name.replace("_", " "),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Text(
                    text = decision.timeStamp,
                    color = Color.Gray,
                    fontSize = 11.sp,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Decision display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(resultColor.copy(alpha = 0.15f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = decisionIcon,
                        fontSize = 48.sp,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = decision.finalDecision.uppercase(),
                        color = resultColor,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = decision.description,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Decision details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DecisionDetailRow(
                    label = "On-field Umpire",
                    value = decision.umpireName,
                )

                DecisionDetailRow(
                    label = "Original Decision",
                    value = decision.originalDecision,
                    valueColor = if (decision.originalDecision.uppercase() == "OUT") colors.statusLive else colors.statusSuccess,
                )

                if (decision.isReviewed) {
                    DecisionDetailRow(
                        label = "Review Status",
                        value = "REVIEWED",
                        valueColor = colors.statusInfo,
                    )

                    decision.reviewOutcome?.let {
                        DecisionDetailRow(
                            label = "Review Outcome",
                            value = it,
                            valueColor = when (it.uppercase()) {
                                "OVERTURNED" -> colors.statusWarning
                                "UPHELD" -> colors.statusSuccess
                                "UMPIRE'S CALL" -> colors.statusWarning
                                else -> Color.White
                            },
                        )
                    }
                }
            }

            // Visual indicator for review
            if (decision.isReviewed) {
                Spacer(modifier = Modifier.height(14.dp))

                // Capture color for Canvas scope
                val statusInfoColor = colors.statusInfo
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2D2D44)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2

                        // Draw TV icon representation
                        val tvWidth = 80.dp.toPx()
                        val tvHeight = 50.dp.toPx()

                        drawRoundRect(
                            color = Color(0xFF3D3D55),
                            topLeft = Offset(centerX - tvWidth / 2, centerY - tvHeight / 2),
                            size = Size(tvWidth, tvHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                        )

                        // Screen
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    statusInfoColor.copy(alpha = 0.3f),
                                    statusInfoColor.copy(alpha = 0.1f),
                                ),
                            ),
                            topLeft = Offset(centerX - tvWidth / 2 + 5.dp.toPx(), centerY - tvHeight / 2 + 5.dp.toPx()),
                            size = Size(tvWidth - 10.dp.toPx(), tvHeight - 10.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                        )

                        // Scanning lines animation
                        val lineY = (centerY - tvHeight / 4) + (tvHeight / 2 * (animatedProgress.value % 1f))
                        drawLine(
                            color = statusInfoColor.copy(alpha = flashPulse),
                            start = Offset(centerX - tvWidth / 2 + 10.dp.toPx(), lineY),
                            end = Offset(centerX + tvWidth / 2 - 10.dp.toPx(), lineY),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }

                    Text(
                        text = "THIRD UMPIRE",
                        color = colors.statusInfo,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DecisionDetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.White,
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 12.sp,
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun UmpireDecisionCardPreview() {
    val colors = BouncerDesignSystem.colors
    UmpireDecisionCard(
        decision = UmpireDecisionData(
            decisionType = DecisionType.LBW,
            originalDecision = "Not Out",
            finalDecision = "OUT",
            umpireName = "Kumar Dharmasena",
            isReviewed = true,
            reviewOutcome = "OVERTURNED",
            timeStamp = "18.4 overs",
            description = "Plumb in front, hitting middle stump",
        ),
    )
}

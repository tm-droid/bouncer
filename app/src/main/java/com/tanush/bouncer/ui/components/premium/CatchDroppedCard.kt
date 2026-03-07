package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Catch/Dropped Catch Card
 *
 * TODO: Integration Requirements:
 * - Fetch fielding events from live match feed
 * - Integrate video replay links for each catch
 * - Calculate catch success rate by fielder
 * - Show expected runs cost from dropped catches
 * - Map catch locations on field diagram
 * - Track difficulty rating from ball-tracking data
 * - Show historical fielding stats per player
 * - Add catch probability model integration
 * - Navigate to fielder profile on tap
 * - Cache fielding events for match review
 */

data class CatchEvent(
    val fielderName: String,
    val batterName: String,
    val bowlerName: String,
    val isCaught: Boolean,
    val isDropped: Boolean,
    val fieldPosition: String,
    val over: String,
    val difficultyRating: Float, // 0.0 to 1.0
    val catchType: String, // "Slip catch", "Diving catch", "Regulation", etc.
)

@Composable
fun CatchDroppedCard(
    catches: List<CatchEvent>,
    teamName: String,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "catch")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(catches) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1000, easing = FastOutSlowInEasing),
        )
    }

    val caughtCount = catches.count { it.isCaught }
    val droppedCount = catches.count { it.isDropped }
    val catchSuccess = if (catches.isNotEmpty()) (caughtCount.toFloat() / catches.size * 100) else 0f

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
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(colors.statusSuccess, colors.statusSuccess.copy(alpha = 0.6f)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sports,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "FIELDING REVIEW",
                            color = colors.goldDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = teamName,
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Success rate
                CatchSuccessRing(
                    progress = catchSuccess / 100f * animatedProgress.value,
                    glowAlpha = glowAlpha,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.glassSurface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CatchStatColumn(
                    value = caughtCount.toString(),
                    label = "Catches Taken",
                    color = colors.statusSuccess,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(colors.textTertiary),
                )

                CatchStatColumn(
                    value = droppedCount.toString(),
                    label = "Dropped",
                    color = colors.statusLive,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(colors.textTertiary),
                )

                CatchStatColumn(
                    value = "${catchSuccess.toInt()}%",
                    label = "Success Rate",
                    color = colors.goldDark,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Individual catch events
            catches.forEachIndexed { index, catchEvent ->
                CatchEventRow(
                    catchEvent = catchEvent,
                    progress = animatedProgress.value,
                    index = index,
                )

                if (index < catches.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun CatchSuccessRing(
    progress: Float,
    glowAlpha: Float,
) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = Modifier.size(60.dp),
        contentAlignment = Alignment.Center,
    ) {
        val statusSuccessC = colors.statusSuccess
        val goldDarkC = colors.goldDark
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2

            // Background ring
            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                radius = radius,
                style = Stroke(width = strokeWidth),
            )

            // Progress ring
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(statusSuccessC, goldDarkC, statusSuccessC),
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Glow effect
            if (progress > 0.7f) {
                drawCircle(
                    color = statusSuccessC.copy(alpha = glowAlpha * 0.3f),
                    radius = radius + 8.dp.toPx(),
                )
            }
        }

        Text(
            text = "${(progress * 100).toInt()}%",
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CatchStatColumn(
    value: String,
    label: String,
    color: Color,
) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun CatchEventRow(
    catchEvent: CatchEvent,
    progress: Float,
    index: Int,
) {
    val colors = BouncerDesignSystem.colors
    val delayedProgress = ((progress - index * 0.1f) / 0.9f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = delayedProgress
                translationX = (1 - delayedProgress) * 30f
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (catchEvent.isCaught) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.statusSuccess.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.statusLive.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    )
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Outcome indicator
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (catchEvent.isCaught) colors.statusSuccess else colors.statusLive,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (catchEvent.isCaught) "✓" else "✗",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Event details
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = catchEvent.fielderName,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = catchEvent.catchType,
                    color = colors.goldDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = "${catchEvent.batterName} off ${catchEvent.bowlerName}",
                color = colors.textSecondary,
                fontSize = 12.sp,
            )

            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Over ${catchEvent.over}",
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                )
                Text(
                    text = "• ${catchEvent.fieldPosition}",
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                )
            }
        }

        // Difficulty rating
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Difficulty",
                color = colors.textSecondary,
                fontSize = 9.sp,
            )

            DifficultyBar(difficulty = catchEvent.difficultyRating)
        }
    }
}

@Composable
private fun DifficultyBar(difficulty: Float) {
    val colors = BouncerDesignSystem.colors
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(5) { index ->
            val isFilled = (index + 1) <= (difficulty * 5).toInt()
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            !isFilled -> colors.glassBorder
                            difficulty > 0.8f -> colors.statusLive
                            difficulty > 0.5f -> colors.statusWarning
                            else -> colors.statusSuccess
                        },
                    ),
            )
        }
    }
}

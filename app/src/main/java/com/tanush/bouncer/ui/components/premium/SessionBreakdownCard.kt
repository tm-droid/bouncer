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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.LiveBlue
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WicketRed

/**
 * Session Breakdown Card (Test Matches)
 *
 * TODO: Integration Requirements:
 * - Track sessions for Test match format
 * - Fetch session-wise stats from match API
 * - Show runs and wickets per session
 * - Calculate session run rates
 * - Track overs bowled per session
 * - Highlight dominant sessions
 * - Compare with historical session patterns
 * - Add session predictions for remaining play
 * - Show weather impact on sessions
 * - Cache session data for day analysis
 */

data class SessionStats(
    val sessionNumber: Int,
    val sessionName: String, // "Session 1", "Pre-Lunch", "Post-Tea"
    val runs: Int,
    val wickets: Int,
    val overs: Float,
    val runRate: Double,
    val startTime: String,
    val endTime: String,
    val isCurrentSession: Boolean = false,
)

@Composable
fun SessionBreakdownCard(
    sessions: List<SessionStats>,
    matchFormat: String, // "Test", "ODI", "T20"
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "session")

    val currentSessionGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(sessions) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing),
        )
    }

    val totalRuns = sessions.sumOf { it.runs }
    val totalWickets = sessions.sumOf { it.wickets }

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
                                    colors = listOf(Color(0xFF00BCD4), Color(0xFF26C6DA)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "SESSION BREAKDOWN",
                            color = ShinyGoldMid,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = matchFormat,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$totalRuns/$totalWickets",
                        color = ShinyGoldMid,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Total",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Session bars visualization
            SessionBarsVisualization(
                sessions = sessions,
                progress = animatedProgress.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Session details
            sessions.forEachIndexed { index, session ->
                SessionRow(
                    session = session,
                    progress = animatedProgress.value,
                    currentGlow = if (session.isCurrentSession) currentSessionGlow else 0f,
                    index = index,
                )

                if (index < sessions.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionBarsVisualization(
    sessions: List<SessionStats>,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val maxRuns = sessions.maxOfOrNull { it.runs } ?: 1

    Canvas(modifier = modifier) {
        val barWidth = size.width / (sessions.size * 2 + 1)
        val maxHeight = size.height * 0.8f

        sessions.forEachIndexed { index, session ->
            val barHeight = (session.runs.toFloat() / maxRuns * maxHeight * progress)
            val x = barWidth + index * barWidth * 2

            // Glow for current session
            if (session.isCurrentSession) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ShinyGoldMid.copy(alpha = 0.3f),
                            Color.Transparent,
                        ),
                    ),
                    topLeft = Offset(x - 10f, size.height - barHeight - 20f),
                    size = androidx.compose.ui.geometry.Size(barWidth + 20f, barHeight + 40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                )
            }

            // Session bar
            val barColor = when {
                session.isCurrentSession -> ShinyGoldMid
                session.runRate > 5.0 -> SuccessGreen
                session.runRate < 3.0 -> WicketRed
                else -> ChartBlue
            }

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(barColor, barColor.copy(alpha = 0.6f)),
                ),
                topLeft = Offset(x, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            )

            // Wicket indicators on bar
            if (session.wickets > 0) {
                repeat(session.wickets.coerceAtMost(5)) { i ->
                    val wicketY = size.height - barHeight + (barHeight / 5 * (i + 0.5f))
                    drawCircle(
                        color = WicketRed,
                        radius = 4f,
                        center = Offset(x + barWidth / 2, wicketY),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionStats,
    progress: Float,
    currentGlow: Float,
    index: Int,
) {
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
                if (session.isCurrentSession) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            ShinyGoldMid.copy(alpha = 0.15f * currentGlow),
                            Color.Transparent,
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    )
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Session badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (session.isCurrentSession) ShinyGoldMid else Color.White.copy(alpha = 0.1f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "S${session.sessionNumber}",
                color = if (session.isCurrentSession) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Session info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.sessionName,
                    color = if (session.isCurrentSession) ShinyGoldMid else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                if (session.isCurrentSession) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(LiveBlue),
                    )
                }
            }

            Text(
                text = "${session.startTime} - ${if (session.isCurrentSession) "now" else session.endTime}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
        }

        // Stats
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${session.runs}/${session.wickets}",
                color = if (session.isCurrentSession) ShinyGoldMid else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${session.overs}ov",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RR: ${String.format("%.2f", session.runRate)}",
                    color = when {
                        session.runRate > 5.0 -> SuccessGreen
                        session.runRate < 3.0 -> WicketRed
                        else -> ChartBlue
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

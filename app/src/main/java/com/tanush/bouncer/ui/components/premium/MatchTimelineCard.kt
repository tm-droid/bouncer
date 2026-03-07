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
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.LiveBlue
import com.tanush.bouncer.ui.theme.ShinyGoldDeep
import com.tanush.bouncer.ui.theme.ShinyGoldLight
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Match timeline card showing key events
 *
 * TODO: Integration Requirements:
 * - Fetch key events from live match feed
 * - Categorize events (wickets, milestones, reviews, etc.)
 * - Add animated event markers on timeline
 * - Link events to video highlights
 * - Allow scrolling through full match timeline
 * - Show win probability changes per event
 * - Add event filtering options
 * - Navigate to detailed event view on tap
 * - Real-time event additions via WebSocket
 * - Cache timeline for offline match review
 */

data class TimelineEventData(
    val over: Float,
    val eventType: TimelineEventType,
    val title: String,
    val description: String,
    val impactLevel: ImpactLevel,
)

enum class TimelineEventType {
    WICKET, MILESTONE, BOUNDARY, POWERPLAY, REVIEW, INJURY, STRATEGIC_TIMEOUT, DROPPED_CATCH
}

enum class ImpactLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Composable
fun MatchTimelineCard(
    events: List<TimelineEventData>,
    currentOver: Float,
    matchPhase: String,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "timeline")
    val currentPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    LaunchedEffect(events) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        )
    }

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
                                    colors = listOf(Color(0xFF00BCD4), Color(0xFF00897B)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "MATCH TIMELINE",
                            color = colors.goldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = matchPhase,
                            color = colors.textPrimary.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.statusInfo.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Over ${currentOver.toInt()}",
                        color = colors.statusInfo,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Timeline visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44)),
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2

                    val maxOver = 20f

                    // Draw timeline bar
                    drawLine(
                        color = Color.White.copy(alpha = 0.2f),
                        start = Offset(0f, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )

                    // Draw progress
                    val progressWidth = (currentOver / maxOver) * width * animatedProgress.value
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(colors.goldDark, colors.goldPrimary),
                        ),
                        start = Offset(0f, centerY),
                        end = Offset(progressWidth, centerY),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )

                    // Draw event markers
                    events.forEach { event ->
                        val x = (event.over / maxOver) * width
                        val markerColor = when (event.impactLevel) {
                            ImpactLevel.CRITICAL -> colors.statusLive
                            ImpactLevel.HIGH -> colors.statusWarning
                            ImpactLevel.MEDIUM -> colors.statusInfo
                            ImpactLevel.LOW -> Color.Gray
                        }

                        if (event.over <= currentOver * animatedProgress.value) {
                            // Event marker
                            drawCircle(
                                color = markerColor,
                                radius = 8.dp.toPx(),
                                center = Offset(x, centerY),
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = Offset(x, centerY),
                            )
                        }
                    }

                    // Current position indicator
                    val currentX = (currentOver / maxOver) * width * animatedProgress.value

                    // Glow effect
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.goldDark.copy(alpha = 0.4f * currentPulse),
                                colors.goldDark.copy(alpha = 0f),
                            ),
                            center = Offset(currentX, centerY),
                            radius = 20.dp.toPx() * currentPulse,
                        ),
                        center = Offset(currentX, centerY),
                        radius = 20.dp.toPx() * currentPulse,
                    )

                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(colors.goldLight, colors.goldDark),
                        ),
                        radius = 10.dp.toPx(),
                        center = Offset(currentX, centerY),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Over labels
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("0", "5", "10", "15", "20").forEach { over ->
                    Text(
                        text = over,
                        color = Color.Gray,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recent events list
            Text(
                text = "KEY EVENTS",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                events.take(5).forEachIndexed { index, event ->
                    TimelineEventRow(
                        event = event,
                        isLatest = index == 0,
                        progress = animatedProgress.value,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineEventRow(
    event: TimelineEventData,
    isLatest: Boolean,
    progress: Float,
) {
    val colors = BouncerDesignSystem.colors
    val impactColor = when (event.impactLevel) {
        ImpactLevel.CRITICAL -> colors.statusLive
        ImpactLevel.HIGH -> colors.statusWarning
        ImpactLevel.MEDIUM -> colors.statusInfo
        ImpactLevel.LOW -> Color.Gray
    }

    val eventLabel = when (event.eventType) {
        TimelineEventType.WICKET -> "W"
        TimelineEventType.MILESTONE -> "★"
        TimelineEventType.BOUNDARY -> "4/6"
        TimelineEventType.POWERPLAY -> "PP"
        TimelineEventType.REVIEW -> "DRS"
        TimelineEventType.INJURY -> "INJ"
        TimelineEventType.STRATEGIC_TIMEOUT -> "TO"
        TimelineEventType.DROPPED_CATCH -> "DC"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isLatest) {
                    impactColor.copy(alpha = 0.15f)
                } else {
                    Color(0xFF2D2D44)
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Over badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(impactColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = eventLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = impactColor,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = event.description,
                color = Color.Gray,
                fontSize = 11.sp,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Over ${String.format("%.1f", event.over)}",
                color = colors.textPrimary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            if (isLatest) {
                Text(
                    text = "Latest",
                    color = impactColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun MatchTimelineCardPreview() {
    val colors = BouncerDesignSystem.colors
    val events = listOf(
        TimelineEventData(
            over = 18.4f,
            eventType = TimelineEventType.WICKET,
            title = "WICKET! Sharma c Warner b Cummins",
            description = "Key wicket at crucial stage",
            impactLevel = ImpactLevel.CRITICAL,
        ),
        TimelineEventData(
            over = 16.0f,
            eventType = TimelineEventType.MILESTONE,
            title = "Kohli reaches 50",
            description = "Off 32 balls, SR 156.25",
            impactLevel = ImpactLevel.HIGH,
        ),
        TimelineEventData(
            over = 12.3f,
            eventType = TimelineEventType.BOUNDARY,
            title = "Three consecutive sixes",
            description = "18 runs in the over",
            impactLevel = ImpactLevel.MEDIUM,
        ),
        TimelineEventData(
            over = 6.0f,
            eventType = TimelineEventType.POWERPLAY,
            title = "Powerplay ends",
            description = "India 58/1 (6.0 ov)",
            impactLevel = ImpactLevel.MEDIUM,
        ),
        TimelineEventData(
            over = 4.2f,
            eventType = TimelineEventType.WICKET,
            title = "WICKET! Gill b Starc",
            description = "Clean bowled by yorker",
            impactLevel = ImpactLevel.HIGH,
        ),
    )

    MatchTimelineCard(
        events = events,
        currentOver = 18.4f,
        matchPhase = "Death Overs",
    )
}

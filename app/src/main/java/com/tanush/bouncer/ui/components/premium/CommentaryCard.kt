package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.GlassSurface
import com.tanush.bouncer.ui.theme.*

/**
 * Live Commentary Card - Technical Timeline Aesthetic.
 * Updated to BouncerImportance.STANDARD for a professional analytical feel.
 */

data class CommentaryEntry(
    val time: String,
    val over: String,
    val text: String,
    val eventType: String?, // "WICKET", "SIX", "FOUR", "REVIEW", null for normal
    val isExciting: Boolean = false,
)

@Composable
fun CommentaryCard(
    commentaryEntries: List<CommentaryEntry>,
    isLive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "commentary")

    val liveIndicatorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liveIndicator",
    )

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(commentaryEntries) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(800, easing = FastOutSlowInEasing),
        )
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        importance = BouncerImportance.STANDARD,
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header - Technical
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.02f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headset,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "LIVE COMMENTARY",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = "STRATEGIC FEED",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Live indicator - Subdued
                if (isLive) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(LiveBlue.copy(alpha = liveIndicatorAlpha)),
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = "LIVE",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Commentary entries
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                commentaryEntries.forEachIndexed { index, entry ->
                    CommentaryEntryRow(
                        entry = entry,
                        progress = animatedProgress.value,
                        index = index,
                        isFirst = index == 0,
                        isLast = index == commentaryEntries.size - 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentaryEntryRow(
    entry: CommentaryEntry,
    progress: Float,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean
) {
    val delayedProgress = ((progress - index * 0.08f) / 0.92f).coerceIn(0f, 1f)

    val eventColor = when (entry.eventType) {
        "WICKET" -> WicketRed
        "SIX" -> BoundarySix
        "FOUR" -> BoundaryFour
        "REVIEW" -> WarningYellow
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) // FIX OVERLAP: Ensure vertical connector line stretches correctly
            .graphicsLayer { alpha = delayedProgress }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Timeline Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(50.dp).fillMaxHeight(),
        ) {
            // Over number - Technical Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (eventColor != null) eventColor.copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.03f)
                    )
                    .border(
                        0.5.dp,
                        if (eventColor != null) eventColor.copy(alpha = 0.3f)
                        else Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = entry.over,
                    color = eventColor ?: TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            // Technical Connector line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                            )
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Commentary content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
        ) {
            // Event badge if applicable
            if (entry.eventType != null) {
                Row(
                    modifier = Modifier.padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EventBadge(eventType = entry.eventType)

                    if (entry.isExciting) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GoldPrimary.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "TRENDING",
                                color = GoldPrimary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // Commentary text
            Text(
                text = entry.text,
                color = if (isFirst) TextPrimary else TextSecondary,
                fontSize = if (isFirst) 14.sp else 13.sp,
                fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Medium,
                lineHeight = 20.sp,
            )

            // Timestamp
            Text(
                text = entry.time.uppercase(),
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun EventBadge(eventType: String) {
    val (color, label) = when (eventType) {
        "WICKET" -> Pair(WicketRed, "WICKET")
        "SIX" -> Pair(BoundarySix, "SIX")
        "FOUR" -> Pair(BoundaryFour, "FOUR")
        "REVIEW" -> Pair(WarningYellow, "REVIEW")
        else -> Pair(TextTertiary, eventType)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.1f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.ThumbUp
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.WicketRed
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Live fan poll card for match engagement
 *
 * TODO: API Integration
 * - Implement backend API for poll creation, submission, and results aggregation
 * - Add WebSocket connection for real-time vote count updates
 * - Integrate user authentication check before allowing votes
 * - Store poll results and user vote history in database
 *
 * TODO: Features
 * - Add poll expiration timer and auto-close functionality
 * - Implement vote change prevention (one vote per user per poll)
 * - Add admin controls for poll management
 * - Support multiple poll types (single choice, multi-choice, ranking)
 *
 * TODO: UI/UX
 * - Add animated vote count transitions
 * - Implement optimistic UI updates for immediate feedback
 * - Show user's previous vote if returning to poll
 */

data class PollOption(
    val text: String,
    val percentage: Float,
    val votes: Int,
    val teamLogo: String? = null,
    val isLeading: Boolean = false,
)

data class LivePollData(
    val question: String,
    val options: List<PollOption>,
    val totalVotes: Int,
    val isActive: Boolean = true,
    val timeRemaining: String? = null,
)

@Composable
fun LivePollCard(
    pollData: LivePollData,
    onVote: ((Int) -> Unit)? = null,
    hasVoted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "poll")

    val liveIndicatorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live",
    )

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box {
            // Shimmer background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.statusInfo.copy(alpha = 0.05f),
                        Color.Transparent,
                    ),
                    start = Offset(size.width * shimmerOffset, 0f),
                    end = Offset(size.width * (shimmerOffset + 0.3f), size.height),
                )
                drawRect(brush = shimmerBrush)
            }

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
                                        colors = listOf(colors.statusInfo, Color(0xFF26C6DA)),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.HowToVote,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "FAN POLL",
                                    color = colors.statusInfo,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                )

                                if (pollData.isActive) {
                                    Spacer(modifier = Modifier.width(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(colors.statusSuccess.copy(alpha = liveIndicatorAlpha)),
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Text(
                                        text = "LIVE",
                                        color = colors.statusSuccess,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Text(
                                text = "${pollData.totalVotes} votes",
                                color = colors.textTertiary,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    pollData.timeRemaining?.let { time ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.statusWarning.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = time,
                                color = colors.statusWarning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Question
                Text(
                    text = pollData.question,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Options
                pollData.options.forEachIndexed { index, option ->
                    PollOptionItem(
                        option = option,
                        index = index,
                        showResults = hasVoted || !pollData.isActive,
                        onVote = if (!hasVoted && pollData.isActive) {
                            { onVote?.invoke(index) }
                        } else {
                            null
                        },
                    )

                    if (index < pollData.options.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (!hasVoted && pollData.isActive) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Tap to vote",
                        color = colors.textTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

@Composable
private fun PollOptionItem(
    option: PollOption,
    index: Int,
    showResults: Boolean,
    onVote: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(showResults) {
        if (showResults) {
            animatedProgress.animateTo(
                targetValue = option.percentage,
                animationSpec = tween(800, easing = FastOutSlowInEasing),
            )
        }
    }

    val optionColor = when (index) {
        0 -> colors.statusInfo
        1 -> colors.statusLive
        2 -> colors.goldDark
        else -> colors.statusSuccess
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (option.isLeading && showResults) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            optionColor.copy(alpha = 0.2f),
                            optionColor.copy(alpha = 0.1f),
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.glassSurface,
                            colors.glassSurface,
                        ),
                    )
                },
            )
            .then(
                if (onVote != null) {
                    Modifier
                } else {
                    Modifier
                },
            ),
    ) {
        // Progress bar
        if (showResults) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                drawRoundRect(
                    color = optionColor.copy(alpha = 0.2f),
                    size = Size(size.width * animatedProgress.value, size.height),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Team logo if available
            option.teamLogo?.let { logo ->
                CoilImage(
                    model = logo,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                )

                Spacer(modifier = Modifier.width(12.dp))
            }

            Text(
                text = option.text,
                color = if (option.isLeading && showResults) optionColor else colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (option.isLeading) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )

            if (showResults) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (option.isLeading) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = null,
                            tint = optionColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = "${(option.percentage * 100).toInt()}%",
                        color = if (option.isLeading) optionColor else colors.textSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                // Vote indicator circle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.glassBorder),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(optionColor.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}

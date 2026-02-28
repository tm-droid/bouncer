package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Live Poll card showing fan voting with animated results
 */

data class LivePollInfo(
    val question: String,
    val options: List<PollOption>,
    val totalVotes: Int,
    val isActive: Boolean = true,
    val userVote: Int? = null, // Index of user's vote, null if not voted
    val timeRemaining: String? = null
)

data class PollOption(
    val text: String,
    val votes: Int,
    val color: Color = ChartBlue
)

@Composable
fun LivePollCard(
    pollInfo: LivePollInfo,
    onVote: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "poll_animation")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_offset"
    )
    
    val voteCounts = remember(pollInfo.options) {
        pollInfo.options.map { 
            Animatable(0f) 
        }
    }
    
    LaunchedEffect(pollInfo.options) {
        pollInfo.options.forEachIndexed { index, option ->
            val targetPercentage = if (pollInfo.totalVotes > 0) {
                option.votes.toFloat() / pollInfo.totalVotes
            } else 0f
            
            voteCounts[index].animateTo(
                targetValue = targetPercentage,
                animationSpec = tween(1500, delayMillis = index * 200, easing = FastOutSlowInEasing)
            )
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D1117)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box {
            // Animated background waves
            if (pollInfo.isActive) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerY = size.height / 2
                    
                    for (i in 0 until 3) {
                        val path = Path()
                        val amplitude = 10f + i * 5f
                        val frequency = 0.02f + i * 0.005f
                        val phase = waveOffset + i * 120f
                        
                        path.moveTo(0f, centerY)
                        
                        var x = 0f
                        while (x <= size.width) {
                            val y = centerY + amplitude * sin((x * frequency + phase * PI / 180f).toFloat())
                            path.lineTo(x, y)
                            x += 5f
                        }
                        
                        drawPath(
                            path = path,
                            color = ChartBlue.copy(alpha = 0.05f - i * 0.01f),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.HowToVote,
                            contentDescription = null,
                            tint = if (pollInfo.isActive) LiveBlue.copy(alpha = pulseAlpha) else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "LIVE POLL",
                                    color = if (pollInfo.isActive) LiveBlue else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                if (pollInfo.isActive) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(LiveBlue.copy(alpha = pulseAlpha))
                                    )
                                }
                            }
                        }
                    }
                    
                    // Vote count and timer
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.People,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatVoteCount(pollInfo.totalVotes),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                        if (pollInfo.timeRemaining != null) {
                            Text(
                                text = pollInfo.timeRemaining,
                                color = WarningYellow,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Question
                Text(
                    text = pollInfo.question,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Poll options
                pollInfo.options.forEachIndexed { index, option ->
                    val percentage = voteCounts.getOrNull(index)?.value ?: 0f
                    val isUserVote = pollInfo.userVote == index
                    
                    PollOptionBar(
                        option = option,
                        percentage = percentage,
                        isUserVote = isUserVote,
                        isVoteable = pollInfo.isActive && pollInfo.userVote == null,
                        onClick = {
                            if (pollInfo.isActive && pollInfo.userVote == null && onVote != null) {
                                onVote(index)
                            }
                        }
                    )
                    
                    if (index < pollInfo.options.size - 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                
                // User vote indicator
                if (pollInfo.userVote != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ThumbUp,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "You voted for \"${pollInfo.options[pollInfo.userVote].text}\"",
                                color = SuccessGreen,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PollOptionBar(
    option: PollOption,
    percentage: Float,
    isUserVote: Boolean,
    isVoteable: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isUserVote) {
        option.color.copy(alpha = 0.3f)
    } else {
        Color.White.copy(alpha = 0.05f)
    }
    
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            // Progress fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percentage)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                option.color.copy(alpha = 0.3f),
                                option.color.copy(alpha = 0.5f)
                            )
                        )
                    )
            )
            
            // Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isUserVote) {
                        Icon(
                            imageVector = Icons.Filled.ThumbUp,
                            contentDescription = null,
                            tint = option.color,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = option.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = if (isUserVote) FontWeight.Bold else FontWeight.Medium
                    )
                }
                
                Text(
                    text = "${(percentage * 100).toInt()}%",
                    color = option.color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Voteable indicator
            if (isVoteable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ThumbUp,
                        contentDescription = "Vote",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

private fun formatVoteCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M votes"
        count >= 1_000 -> "${count / 1_000}K votes"
        else -> "$count votes"
    }
}

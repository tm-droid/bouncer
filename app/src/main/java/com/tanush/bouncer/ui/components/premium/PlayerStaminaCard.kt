package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.FitnessCenter
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed

/**
 * Player Stamina Card
 *
 * TODO: Integration Requirements:
 * - Develop stamina estimation model based on workload
 * - Track overs bowled and rest periods
 * - Integrate speed drop detection for fatigue
 * - Show stamina recovery predictions
 * - Add injury risk indicators
 * - Compare with typical match stamina curves
 * - Factor in conditions (heat, humidity)
 * - Show historical stamina patterns
 * - Alert for high fatigue levels
 * - Cache stamina data for match analysis
 */

data class PlayerStaminaData(
    val playerName: String,
    val playerImage: String,
    val playerRole: String, // "Bowler", "Batter", "Allrounder"
    val currentStamina: Float, // 0.0 - 1.0
    val fatigueLevel: String, // "Fresh", "Moderate", "Tired", "Exhausted"
    val oversOrBallsFaced: Float,
    val restTimeSinceLastSpell: String?,
)

@Composable
fun PlayerStaminaCard(
    players: List<PlayerStaminaData>,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stamina")

    val staminaPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
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
            Canvas(modifier = Modifier.fillMaxSize()) {
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        SuccessGreen.copy(alpha = 0.08f),
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
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(24.dp),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "PLAYER STAMINA",
                        color = SuccessGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                players.forEach { player ->
                    PlayerStaminaRow(
                        player = player,
                        staminaPulse = staminaPulse,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerStaminaRow(
    player: PlayerStaminaData,
    staminaPulse: Float,
    modifier: Modifier = Modifier,
) {
    val staminaColor = when {
        player.currentStamina >= 0.7f -> SuccessGreen
        player.currentStamina >= 0.4f -> WarningYellow
        player.currentStamina >= 0.2f -> WarningYellow.copy(red = 1f)
        else -> WicketRed
    }

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(player.currentStamina) {
        animatedProgress.animateTo(
            targetValue = player.currentStamina,
            animationSpec = tween(1000, easing = FastOutSlowInEasing),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Player image
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(staminaColor.copy(alpha = 0.2f * staminaPulse)),
            contentAlignment = Alignment.Center,
        ) {
            CoilImage(
                model = player.playerImage,
                contentDescription = player.playerName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = player.playerName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = player.fatigueLevel,
                    color = staminaColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = player.playerRole,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Stamina bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            ) {
                // Background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.1f)),
                )

                // Progress
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = animatedProgress.value)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    staminaColor,
                                    staminaColor.copy(alpha = 0.7f),
                                ),
                            ),
                        ),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (player.playerRole == "Bowler") {
                        "${player.oversOrBallsFaced} overs"
                    } else {
                        "${player.oversOrBallsFaced.toInt()} balls"
                    },
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                )

                player.restTimeSinceLastSpell?.let { restTime ->
                    Text(
                        text = "Rest: $restTime",
                        color = SuccessGreen.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                    )
                }

                Text(
                    text = "${(player.currentStamina * 100).toInt()}%",
                    color = staminaColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

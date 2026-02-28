package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Player comparison card for head-to-head stats
 *
 * TODO: Integration Requirements:
 * - Fetch player statistics from player stats API
 * - Load player images from reliable image CDN
 * - Implement player search/selection picker
 * - Add format filter (Test/ODI/T20/IPL etc.)
 * - Cache player stats for offline viewing
 * - Add historical comparison trends over time
 * - Implement animated transitions for stat bars
 * - Add loading states while fetching player data
 * - Handle missing player images gracefully
 * - Add share comparison as image functionality
 * - Navigate to player profile on tap
 */

data class PlayerCompareData(
    val player1: ComparePlayerInfo,
    val player2: ComparePlayerInfo,
    val stats: List<CompareStatItem>,
)

data class ComparePlayerInfo(
    val name: String,
    val teamName: String,
    val imageUrl: String?,
    val role: String,
)

data class CompareStatItem(
    val label: String,
    val player1Value: Float,
    val player2Value: Float,
    val displayValue1: String,
    val displayValue2: String,
    val higherIsBetter: Boolean = true,
)

@Composable
fun PlayerComparisonCard(
    comparisonData: PlayerCompareData,
    format: String,
    modifier: Modifier = Modifier,
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(comparisonData) {
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
                                    colors = listOf(ShinyGoldMid, ShinyGoldDeep),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Compare,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "PLAYER COMPARISON",
                            color = ShinyGoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = format,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Players header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Player 1
                PlayerCompareHeader(
                    player = comparisonData.player1,
                    color = ChartBlue,
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.Start,
                )

                // VS
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2D2D44)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "VS",
                        color = ShinyGoldMid,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                // Player 2
                PlayerCompareHeader(
                    player = comparisonData.player2,
                    color = WicketRed,
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.End,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stats comparison
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                comparisonData.stats.forEach { stat ->
                    CompareStatRow(
                        stat = stat,
                        progress = animatedProgress.value,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Overall winner indicator
            val player1Wins = comparisonData.stats.count { stat ->
                if (stat.higherIsBetter) {
                    stat.player1Value > stat.player2Value
                } else {
                    stat.player1Value < stat.player2Value
                }
            }
            val player2Wins = comparisonData.stats.size - player1Wins

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Player 1 wins
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "$player1Wins",
                            color = if (player1Wins > player2Wins) SuccessGreen else Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "Categories Won",
                            color = Color.Gray,
                            fontSize = 10.sp,
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(Color.White.copy(alpha = 0.1f)),
                    )

                    // Player 2 wins
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "$player2Wins",
                            color = if (player2Wins > player1Wins) SuccessGreen else Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "Categories Won",
                            color = Color.Gray,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerCompareHeader(
    player: ComparePlayerInfo,
    color: Color,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            if (player.imageUrl != null) {
                CoilImage(
                    model = player.imageUrl,
                    contentDescription = player.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = player.name.take(2).uppercase(),
                    color = color,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = player.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = player.teamName,
            color = Color.Gray,
            fontSize = 11.sp,
        )

        Text(
            text = player.role,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CompareStatRow(
    stat: CompareStatItem,
    progress: Float,
) {
    val total = stat.player1Value + stat.player2Value
    val player1Ratio = if (total > 0) stat.player1Value / total else 0.5f

    val player1IsWinner = if (stat.higherIsBetter) {
        stat.player1Value >= stat.player2Value
    } else {
        stat.player1Value <= stat.player2Value
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stat.displayValue1,
                color = if (player1IsWinner) SuccessGreen else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = stat.label,
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = stat.displayValue2,
                color = if (!player1IsWinner) SuccessGreen else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Comparison bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            // Player 1 bar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight((player1Ratio * progress).coerceAtLeast(0.05f))
                    .background(
                        if (player1IsWinner) SuccessGreen else ChartBlue,
                    ),
            )

            Spacer(modifier = Modifier.width(2.dp))

            // Player 2 bar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(((1f - player1Ratio) * progress).coerceAtLeast(0.05f))
                    .background(
                        if (!player1IsWinner) SuccessGreen else WicketRed,
                    ),
            )
        }
    }
}

@Composable
fun PlayerComparisonCardPreview() {
    PlayerComparisonCard(
        comparisonData = PlayerCompareData(
            player1 = ComparePlayerInfo(
                name = "Virat Kohli",
                teamName = "India",
                imageUrl = null,
                role = "Batsman",
            ),
            player2 = ComparePlayerInfo(
                name = "Steve Smith",
                teamName = "Australia",
                imageUrl = null,
                role = "Batsman",
            ),
            stats = listOf(
                CompareStatItem("Runs", 12000f, 9200f, "12,000", "9,200"),
                CompareStatItem("Average", 52.4f, 48.6f, "52.4", "48.6"),
                CompareStatItem("Strike Rate", 138.5f, 128.2f, "138.5", "128.2"),
                CompareStatItem("Centuries", 46f, 32f, "46", "32"),
                CompareStatItem("50s", 65f, 58f, "65", "58"),
                CompareStatItem("Highest", 183f, 164f, "183", "164"),
            ),
        ),
        format = "T20 International",
    )
}

package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*

/**
 * Series Awards Card
 *
 * TODO: Integration Requirements:
 * - Fetch series awards from series/tournament API
 * - Show Player of Series with stats
 * - Display category awards (best batter, bowler, etc.)
 * - Load player images from CDN
 * - Add awards history for series
 * - Show emerging player awards
 * - Navigate to player profile on tap
 * - Add social sharing functionality
 * - Display award criteria and voting
 * - Cache awards after series completion
 */

data class SeriesAwardData(
    val title: String,
    val playerName: String,
    val playerImage: String,
    val teamName: String,
    val stats: String,
    val statDetails: String,
)

data class SeriesLeaderData(
    val category: String, // "Most Runs", "Most Wickets", "Most Catches"
    val leaders: List<LeaderEntry>,
)

data class LeaderEntry(
    val rank: Int,
    val playerName: String,
    val playerImage: String,
    val teamShort: String,
    val statValue: String,
)

@Composable
fun SeriesAwardsCard(
    awards: List<SeriesAwardData>,
    leaders: List<SeriesLeaderData>,
    seriesName: String,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "awards")

    val starRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "star",
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

    var selectedCategory by remember { mutableIntStateOf(0) }

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
                        ShinyGoldMid.copy(alpha = 0.1f),
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
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = ShinyGoldMid,
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer { rotationZ = starRotation },
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = "SERIES AWARDS",
                                color = ShinyGoldMid,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )
                            Text(
                                text = seriesName,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // Awards section
                if (awards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        awards.forEach { award ->
                            AwardCard(award = award)
                        }
                    }
                }

                // Leaders section
                if (leaders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))

                    // Category tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        leaders.forEachIndexed { index, leader ->
                            CategoryTab(
                                category = leader.category,
                                isSelected = selectedCategory == index,
                                onClick = { selectedCategory = index },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Leader list
                    if (selectedCategory < leaders.size) {
                        leaders[selectedCategory].leaders.forEach { entry ->
                            LeaderRow(entry = entry)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AwardCard(
    award: SeriesAwardData,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ShinyGoldMid.copy(alpha = 0.2f),
                        ShinyGoldDeep.copy(alpha = 0.1f),
                    ),
                ),
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = award.title,
            color = ShinyGoldMid,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(ShinyGoldMid.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            CoilImage(
                model = award.playerImage,
                contentDescription = award.playerName,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = award.playerName,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = award.teamName,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = award.stats,
            color = ShinyGoldMid,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = award.statDetails,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun CategoryTab(
    category: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) {
                    ShinyGoldMid.copy(alpha = 0.2f)
                } else {
                    Color.White.copy(alpha = 0.05f)
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = category,
            color = if (isSelected) ShinyGoldMid else Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun LeaderRow(
    entry: LeaderEntry,
    modifier: Modifier = Modifier,
) {
    val rankColor = when (entry.rank) {
        1 -> ShinyGoldMid
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> Color.White.copy(alpha = 0.6f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (entry.rank <= 3) {
                    rankColor.copy(alpha = 0.1f)
                } else {
                    Color.White.copy(alpha = 0.03f)
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rank
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(rankColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${entry.rank}",
                color = rankColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Player image
        CoilImage(
            model = entry.playerImage,
            contentDescription = entry.playerName,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.playerName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = entry.teamShort,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
            )
        }

        Text(
            text = entry.statValue,
            color = if (entry.rank == 1) ShinyGoldMid else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

package com.tanush.bouncer.ui.components.premium

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
import androidx.compose.material.icons.filled.TableChart
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.ShinyGoldDeep
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed

/**
 * Series Standings Card
 *
 * TODO: Integration Requirements:
 * - Fetch points table from series/tournament API
 * - Update standings in real-time during matches
 * - Show qualification scenarios
 * - Display NRR and tiebreaker rules
 * - Add position change animations
 * - Highlight playoff positions
 * - Show remaining fixtures impact
 * - Navigate to team details on tap
 * - Add standings projection calculator
 * - Cache standings for offline viewing
 */

data class SeriesTeam(
    val name: String,
    val shortName: String,
    val logo: String,
    val played: Int,
    val won: Int,
    val lost: Int,
    val tied: Int,
    val noResult: Int,
    val netRunRate: Float,
    val points: Int,
    val recentForm: List<String> = emptyList(), // W, L, T, NR
)

@Composable
fun SeriesStandingsCard(
    seriesName: String,
    teams: List<SeriesTeam>,
    currentMatchTeams: Pair<String, String>? = null,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "standings")

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
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
            // Shimmer effect
            Canvas(modifier = Modifier.fillMaxSize()) {
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        ShinyGoldMid.copy(alpha = 0.08f),
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
                                        colors = listOf(ShinyGoldDeep, ShinyGoldMid),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "STANDINGS",
                                color = ShinyGoldMid,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )
                            Text(
                                text = seriesName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Table header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "#",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(20.dp),
                    )
                    Text(
                        text = "TEAM",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TableHeaderText("P", Modifier.width(24.dp))
                    TableHeaderText("W", Modifier.width(24.dp))
                    TableHeaderText("L", Modifier.width(24.dp))
                    TableHeaderText("NRR", Modifier.width(50.dp))
                    TableHeaderText("PTS", Modifier.width(30.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Team rows
                teams.forEachIndexed { index, team ->
                    val isCurrentMatchTeam = currentMatchTeams?.let {
                        team.shortName == it.first || team.shortName == it.second
                    } ?: false

                    val isQualified = index < 4 // Top 4 qualify typically

                    StandingsTeamRow(
                        position = index + 1,
                        team = team,
                        isCurrentMatchTeam = isCurrentMatchTeam,
                        isQualified = isQualified,
                    )

                    if (index < teams.lastIndex) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Qualification zone legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LegendItem(
                        color = SuccessGreen,
                        label = "Qualified",
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    LegendItem(
                        color = WarningYellow,
                        label = "In Contention",
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    LegendItem(
                        color = WicketRed,
                        label = "Eliminated",
                    )
                }
            }
        }
    }
}

@Composable
private fun TableHeaderText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun StandingsTeamRow(
    position: Int,
    team: SeriesTeam,
    isCurrentMatchTeam: Boolean,
    isQualified: Boolean,
    modifier: Modifier = Modifier,
) {
    val positionColor = when {
        position <= 2 -> SuccessGreen
        position <= 4 -> WarningYellow
        else -> WicketRed.copy(alpha = 0.5f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isCurrentMatchTeam) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            ShinyGoldMid.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent,
                        ),
                    )
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Position
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(positionColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$position",
                color = positionColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Team info
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoilImage(
                model = team.logo,
                contentDescription = team.name,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = team.shortName,
                    color = if (isCurrentMatchTeam) ShinyGoldMid else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )

                // Recent form
                if (team.recentForm.isNotEmpty()) {
                    Row {
                        team.recentForm.takeLast(5).forEach { result ->
                            val resultColor = when (result) {
                                "W" -> SuccessGreen
                                "L" -> WicketRed
                                "T" -> WarningYellow
                                else -> Color.Gray
                            }
                            Text(
                                text = result,
                                color = resultColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        // Stats
        TableDataText(
            text = "${team.played}",
            modifier = Modifier.width(24.dp),
        )
        TableDataText(
            text = "${team.won}",
            color = SuccessGreen,
            modifier = Modifier.width(24.dp),
        )
        TableDataText(
            text = "${team.lost}",
            color = WicketRed.copy(alpha = 0.7f),
            modifier = Modifier.width(24.dp),
        )
        TableDataText(
            text = if (team.netRunRate >= 0) {
                "+${String.format("%.2f", team.netRunRate)}"
            } else {
                String.format("%.2f", team.netRunRate)
            },
            color = if (team.netRunRate >= 0) SuccessGreen else WicketRed,
            modifier = Modifier.width(50.dp),
        )

        // Points - highlighted
        Box(
            modifier = Modifier
                .width(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ShinyGoldMid.copy(alpha = 0.2f))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${team.points}",
                color = ShinyGoldMid,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TableDataText(
    text: String,
    color: Color = Color.White.copy(alpha = 0.8f),
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 9.sp,
        )
    }
}

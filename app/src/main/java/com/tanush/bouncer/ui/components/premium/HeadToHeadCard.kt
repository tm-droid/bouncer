package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Head to head history card showing team vs team stats
 *
 * TODO: Integration Requirements:
 * - Fetch historical H2H data from cricket API (CricAPI/ESPNCricinfo)
 * - Load team logos dynamically from team data service
 * - Add filter options for format (Test/ODI/T20) and venue
 * - Implement caching for H2H stats to reduce API calls
 * - Add navigation to detailed H2H history view
 * - Show recent encounters with expandable match details
 * - Add loading skeleton while fetching data
 * - Handle error states when API unavailable
 * - Add share functionality for H2H stats
 */

data class HeadToHeadStats(
    val team1Name: String,
    val team2Name: String,
    val team1Wins: Int,
    val team2Wins: Int,
    val draws: Int,
    val noResults: Int,
    val recentMatches: List<H2HMatch>,
    val venueRecord: VenueRecord?,
)

data class H2HMatch(
    val date: String,
    val venue: String,
    val format: String,
    val winner: String?,
    val team1Score: String,
    val team2Score: String,
    val margin: String,
)

data class VenueRecord(
    val venueName: String,
    val team1Wins: Int,
    val team2Wins: Int,
    val totalMatches: Int,
)

@Composable
fun HeadToHeadCard(
    stats: HeadToHeadStats,
    team1Color: Color = BouncerDesignSystem.colors.statusInfo,
    team2Color: Color = BouncerDesignSystem.colors.statusLive,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(stats) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    val totalMatches = stats.team1Wins + stats.team2Wins + stats.draws + stats.noResults
    val team1WinPercent = if (totalMatches > 0) stats.team1Wins.toFloat() / totalMatches else 0f
    val team2WinPercent = if (totalMatches > 0) stats.team2Wins.toFloat() / totalMatches else 0f

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
                                    colors = listOf(GoldPrimary, GoldDark),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "HEAD TO HEAD",
                            color = colors.goldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = "$totalMatches matches",
                            color = Color.Gray,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Head to head visualization
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Team 1
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stats.team1Name,
                        color = team1Color,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${(stats.team1Wins * animatedProgress.value).toInt()}",
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = "wins",
                        color = Color.Gray,
                        fontSize = 12.sp,
                    )
                }

                // VS
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2D2D44)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "VS",
                        color = colors.goldDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Team 2
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stats.team2Name,
                        color = team2Color,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${(stats.team2Wins * animatedProgress.value).toInt()}",
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = "wins",
                        color = Color.Gray,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Win ratio bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (stats.team1Wins > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight((team1WinPercent * animatedProgress.value).coerceAtLeast(0.01f))
                                .background(team1Color),
                        )
                    }
                    if (stats.draws + stats.noResults > 0) {
                        val drawPercent = (stats.draws + stats.noResults).toFloat() / totalMatches
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight((drawPercent * animatedProgress.value).coerceAtLeast(0.01f))
                                .background(Color.Gray),
                        )
                    }
                    if (stats.team2Wins > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight((team2WinPercent * animatedProgress.value).coerceAtLeast(0.01f))
                                .background(team2Color),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Percentages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${(team1WinPercent * 100).toInt()}%",
                    color = team1Color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )

                if (stats.draws > 0 || stats.noResults > 0) {
                    Text(
                        text = "D: ${stats.draws} NR: ${stats.noResults}",
                        color = Color.Gray,
                        fontSize = 10.sp,
                    )
                }

                Text(
                    text = "${(team2WinPercent * 100).toInt()}%",
                    color = team2Color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Recent matches
            if (stats.recentMatches.isNotEmpty()) {
                Text(
                    text = "RECENT ENCOUNTERS",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    stats.recentMatches.take(3).forEach { match ->
                        val isTeam1Winner = match.winner == stats.team1Name
                        val isTeam2Winner = match.winner == stats.team2Name

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2D2D44))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Winner indicator
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        when {
                                            isTeam1Winner -> team1Color
                                            isTeam2Winner -> team2Color
                                            else -> Color.Gray
                                        },
                                    ),
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = match.venue,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = match.format,
                                        color = colors.goldDark,
                                        fontSize = 10.sp,
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${match.team1Score} vs ${match.team2Score}",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                    )
                                    Text(
                                        text = match.date,
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "${match.winner ?: "No Result"} ${if (match.winner != null) "won by ${match.margin}" else ""}",
                                    color = when {
                                        isTeam1Winner -> team1Color
                                        isTeam2Winner -> team2Color
                                        else -> Color.Gray
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            // Venue record
            stats.venueRecord?.let { venue ->
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.2f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "AT THIS VENUE",
                            color = colors.goldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = venue.venueName,
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${venue.team1Wins}",
                                color = team1Color,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stats.team1Name.take(3),
                                color = Color.Gray,
                                fontSize = 10.sp,
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${venue.team2Wins}",
                                color = team2Color,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stats.team2Name.take(3),
                                color = Color.Gray,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeadToHeadCardPreview() {
    val colors = BouncerDesignSystem.colors
    val stats = HeadToHeadStats(
        team1Name = "India",
        team2Name = "Australia",
        team1Wins = 58,
        team2Wins = 52,
        draws = 0,
        noResults = 5,
        recentMatches = listOf(
            H2HMatch("Nov 19, 2023", "Ahmedabad", "ODI WC Final", "Australia", "240/10", "241/4", "6 wickets"),
            H2HMatch("Oct 8, 2023", "Chennai", "ODI WC", "India", "399/5", "304/10", "95 runs"),
            H2HMatch("Mar 22, 2023", "Ahmedabad", "ODI", "India", "118/10", "117/10", "99 runs"),
        ),
        venueRecord = VenueRecord("Wankhede Stadium", 5, 3, 8),
    )

    HeadToHeadCard(stats = stats)
}

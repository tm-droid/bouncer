package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.ShinyGoldLight
import com.tanush.bouncer.ui.theme.ShinyGoldMid
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed

/**
 * Net run rate card
 *
 * TODO: Integration Requirements:
 * - Calculate NRR in real-time from match data
 * - Track NRR history throughout tournament
 * - Show qualification scenarios based on NRR
 * - Compare with other teams in points table
 * - Show required NRR for playoff qualification
 * - Add projected NRR based on current match
 * - Display NRR swing in match
 * - Show historical NRR trends
 * - Navigate to points table on tap
 * - Cache NRR data for tournament standings
 */

data class NetRunRateData(
    val teamName: String,
    val currentNRR: Float,
    val nrrHistory: List<NRRHistoryItem>,
    val runsScored: Int,
    val oversFaced: Float,
    val runsConceded: Int,
    val oversBowled: Float,
    val trend: NRRTrend,
    val tournamentPosition: Int,
)

data class NRRHistoryItem(
    val matchNumber: Int,
    val nrr: Float,
    val opponent: String,
)

enum class NRRTrend {
    IMPROVING, DECLINING, STABLE
}

@Composable
fun NetRunRateCard(
    nrrData: NetRunRateData,
    modifier: Modifier = Modifier,
) {
    val animatedProgress = remember { Animatable(0f) }
    val animatedNRR = remember { Animatable(0f) }

    LaunchedEffect(nrrData) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        )
        animatedNRR.animateTo(
            targetValue = nrrData.currentNRR,
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        )
    }

    val nrrColor = when {
        nrrData.currentNRR > 1f -> SuccessGreen
        nrrData.currentNRR > 0f -> ChartBlue
        nrrData.currentNRR > -1f -> WarningYellow
        else -> WicketRed
    }

    val trendIcon = when (nrrData.trend) {
        NRRTrend.IMPROVING -> "📈"
        NRRTrend.DECLINING -> "📉"
        NRRTrend.STABLE -> "➡️"
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
                                    colors = listOf(nrrColor, nrrColor.copy(alpha = 0.6f)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.ShowChart,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "NET RUN RATE",
                            color = ShinyGoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = nrrData.teamName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2D2D44))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "#${nrrData.tournamentPosition}",
                        color = ShinyGoldMid,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main NRR display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(nrrColor.copy(alpha = 0.15f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = trendIcon,
                        fontSize = 28.sp,
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%+.3f", animatedNRR.value),
                            color = nrrColor,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = when (nrrData.trend) {
                                NRRTrend.IMPROVING -> "Improving"
                                NRRTrend.DECLINING -> "Declining"
                                NRRTrend.STABLE -> "Stable"
                            },
                            color = nrrColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // NRR calculation breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                NRRBreakdownItem(
                    label = "For",
                    runs = nrrData.runsScored,
                    overs = nrrData.oversFaced,
                    runRate = nrrData.runsScored / nrrData.oversFaced,
                    color = SuccessGreen,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(60.dp)
                        .background(Color.White.copy(alpha = 0.1f)),
                )

                NRRBreakdownItem(
                    label = "Against",
                    runs = nrrData.runsConceded,
                    overs = nrrData.oversBowled,
                    runRate = nrrData.runsConceded / nrrData.oversBowled,
                    color = WicketRed,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // NRR history graph
            if (nrrData.nrrHistory.isNotEmpty()) {
                Text(
                    text = "NRR PROGRESSION",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2D2D44))
                        .padding(12.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        val allNRR = nrrData.nrrHistory.map { it.nrr }
                        val maxNRR = (allNRR.maxOrNull() ?: 2f).coerceAtLeast(1f)
                        val minNRR = (allNRR.minOrNull() ?: -2f).coerceAtMost(-1f)
                        val range = maxNRR - minNRR

                        // Zero line
                        val zeroY = height * (1f - (-minNRR / range))
                        drawLine(
                            color = Color.White.copy(alpha = 0.3f),
                            start = Offset(0f, zeroY),
                            end = Offset(width, zeroY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
                        )

                        // NRR line
                        val animatedHistory = nrrData.nrrHistory.take(
                            (nrrData.nrrHistory.size * animatedProgress.value).toInt().coerceAtLeast(1),
                        )

                        val points = animatedHistory.mapIndexed { index, item ->
                            val x = width * index / (nrrData.nrrHistory.size - 1).coerceAtLeast(1)
                            val normalizedNRR = ((item.nrr - minNRR) / range).coerceIn(0f, 1f)
                            val y = height * (1f - normalizedNRR)
                            Offset(x, y)
                        }

                        // Draw connecting lines with gradient
                        points.forEachIndexed { index, point ->
                            if (index > 0) {
                                val prevPoint = points[index - 1]
                                val prevNRR = animatedHistory[index - 1].nrr
                                val currNRR = animatedHistory[index].nrr

                                val lineColor = when {
                                    currNRR > prevNRR -> SuccessGreen
                                    currNRR < prevNRR -> WicketRed
                                    else -> ChartBlue
                                }

                                drawLine(
                                    color = lineColor,
                                    start = prevPoint,
                                    end = point,
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }

                            // Point marker
                            val pointColor = if (animatedHistory[index].nrr >= 0) SuccessGreen else WicketRed
                            drawCircle(
                                color = pointColor,
                                radius = 4.dp.toPx(),
                                center = point,
                            )
                        }
                    }
                }

                // Match labels
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    nrrData.nrrHistory.take(5).forEach { item ->
                        Text(
                            text = "M${item.matchNumber}",
                            color = Color.Gray,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NRRBreakdownItem(
    label: String,
    runs: Int,
    overs: Float,
    runRate: Float,
    color: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2D2D44))
            .padding(12.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "$runs runs",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = String.format("%.1f overs", overs),
            color = Color.Gray,
            fontSize = 11.sp,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = String.format("RR: %.2f", runRate),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun NetRunRateCardPreview() {
    NetRunRateCard(
        nrrData = NetRunRateData(
            teamName = "India",
            currentNRR = 1.256f,
            nrrHistory = listOf(
                NRRHistoryItem(1, 0.5f, "PAK"),
                NRRHistoryItem(2, 0.8f, "AUS"),
                NRRHistoryItem(3, 0.2f, "NZ"),
                NRRHistoryItem(4, 1.1f, "SA"),
                NRRHistoryItem(5, 1.256f, "ENG"),
            ),
            runsScored = 1245,
            oversFaced = 98.4f,
            runsConceded = 1020,
            oversBowled = 100f,
            trend = NRRTrend.IMPROVING,
            tournamentPosition = 1,
        ),
    )
}

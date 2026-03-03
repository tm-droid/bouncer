package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.GlassSurface
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Premium Sleek Scorecard Comparison Card.
 * Updated to BouncerImportance.STANDARD for better visual hierarchy.
 */

data class InningsComparisonData(
    val innings1: InningsSummaryData,
    val innings2: InningsSummaryData,
)

data class InningsSummaryData(
    val teamName: String,
    val teamShort: String,
    val totalRuns: Int,
    val wickets: Int,
    val overs: Float,
    val runRate: Float,
    val fours: Int,
    val sixes: Int,
    val extras: Int,
    val highestScore: Pair<String, Int>,
    val bestBowler: Pair<String, String>,
)

@Composable
fun ScorecardComparisonCard(
    comparison: InningsComparisonData,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
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
            // Header - Subdued & Technical
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = null,
                    tint = TextTertiary, // Subdued from gold
                    modifier = Modifier.size(16.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "INNINGS COMPARISON",
                    color = TextTertiary, // Subdued from gold
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Teams Score Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScoreHeader(
                    team = comparison.innings1.teamShort,
                    score = "${comparison.innings1.totalRuns}/${comparison.innings1.wickets}",
                    overs = "${comparison.innings1.overs} OV",
                    color = colors.statusInfo,
                    modifier = Modifier.weight(1f)
                )

                // Refined VS Badge - Minimalist
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.White.copy(alpha = 0.03f), CircleShape)
                        .border(0.5.dp, Color.White.copy(alpha = 0.05f), CircleShape)
                ) {
                    Text(
                        text = "VS",
                        color = TextTertiary.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                ScoreHeader(
                    team = comparison.innings2.teamShort,
                    score = "${comparison.innings2.totalRuns}/${comparison.innings2.wickets}",
                    overs = "${comparison.innings2.overs} OV",
                    color = colors.statusLive,
                    isRightAligned = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Comparison Metrics Section
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ComparisonMetric(
                    label = "RUN RATE",
                    value1 = comparison.innings1.runRate,
                    value2 = comparison.innings2.runRate,
                    format = "%.2f",
                    color1 = colors.statusInfo,
                    color2 = colors.statusLive
                )

                ComparisonMetric(
                    label = "BOUNDARIES",
                    value1 = (comparison.innings1.fours + comparison.innings1.sixes).toFloat(),
                    value2 = (comparison.innings2.fours + comparison.innings2.sixes).toFloat(),
                    format = "%.0f",
                    color1 = colors.statusInfo,
                    color2 = colors.statusLive
                )

                ComparisonMetric(
                    label = "SIXES",
                    value1 = comparison.innings1.sixes.toFloat(),
                    value2 = comparison.innings2.sixes.toFloat(),
                    format = "%.0f",
                    color1 = colors.statusInfo,
                    color2 = colors.statusLive
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Performance Layer - Standard Surface
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PerformerColumn(
                    title = "TOP SCORER",
                    val1 = comparison.innings1.highestScore,
                    val2 = comparison.innings2.highestScore,
                    modifier = Modifier.weight(1f)
                )
                PerformerColumn(
                    title = "BEST BOWLER",
                    val1 = comparison.innings1.bestBowler.first to comparison.innings1.bestBowler.second,
                    val2 = comparison.innings2.bestBowler.first to comparison.innings2.bestBowler.second,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ScoreHeader(
    team: String,
    score: String,
    overs: String,
    color: Color,
    isRightAligned: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = modifier,
        horizontalAlignment = if (isRightAligned) Alignment.End else Alignment.Start
    ) {
        Text(
            text = team,
            color = color.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
        Text(
            text = score,
            color = TextPrimary,
            fontSize = 22.sp, // Slightly smaller to avoid competition
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = overs,
            color = TextTertiary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ComparisonMetric(
    label: String,
    value1: Float,
    value2: Float,
    format: String,
    color1: Color,
    color2: Color
) {
    val colors = BouncerDesignSystem.colors
    val total = (value1 + value2).coerceAtLeast(1f)
    val ratio1 = value1 / total
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(format, value1),
                color = if (value1 >= value2) color1.copy(alpha = 0.8f) else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = label,
                color = TextTertiary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = String.format(format, value2),
                color = if (value2 > value1) color2.copy(alpha = 0.8f) else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Sleek Layered Progress Bar - Subtle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp) // Thinner
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.03f))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(ratio1.coerceAtLeast(0.01f))
                        .background(color1.copy(alpha = 0.7f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight((1f - ratio1).coerceAtLeast(0.01f))
                        .background(color2.copy(alpha = 0.7f))
                )
            }
        }
    }
}

@Composable
private fun PerformerColumn(
    title: String,
    val1: Pair<String, Any>,
    val2: Pair<String, Any>,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    GlassSurface(
        modifier = modifier,
        importance = BouncerImportance.MINIMAL,
        cornerRadius = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = title,
                color = TextTertiary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            PerformerRow(name = val1.first, stat = val1.second.toString(), color = colors.statusInfo)
            Spacer(modifier = Modifier.height(6.dp))
            PerformerRow(name = val2.first, stat = val2.second.toString(), color = colors.statusLive)
        }
    }
}

@Composable
private fun PerformerRow(name: String, stat: String, color: Color) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name.take(10),
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = stat,
            color = color.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

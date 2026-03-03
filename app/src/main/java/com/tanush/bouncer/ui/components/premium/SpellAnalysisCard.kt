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
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Bowler spell analysis showing spell-by-spell performance
 *
 * TODO: Integration Requirements:
 * - Track bowling spells from match feed
 * - Calculate spell-wise economy and strike rate
 * - Show spell timing and rest periods
 * - Compare spell effectiveness
 * - Add ball-by-ball breakdown per spell
 * - Show spell summary with key deliveries
 * - Track variations used per spell
 * - Navigate to spell details on tap
 * - Add spell comparison with career averages
 * - Cache spell data for bowler analysis
 */

data class BowlerSpell(
    val spellNumber: Int,
    val overs: String,
    val maidens: Int,
    val runs: Int,
    val wickets: Int,
    val dots: Int,
    val boundaries: Int,
    val startOver: Int,
    val endOver: Int,
)

@Composable
fun SpellAnalysisCard(
    bowlerName: String,
    spells: List<BowlerSpell>,
    totalOvers: String,
    totalWickets: Int,
    totalRuns: Int,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(spells) {
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
                                    colors = listOf(colors.goldDark, colors.goldPrimary),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Timeline,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "SPELL ANALYSIS",
                            color = colors.goldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = bowlerName,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Total figures
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$totalWickets/$totalRuns",
                        color = colors.goldDark,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$totalOvers overs",
                        color = Color.Gray,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Spell timeline visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val maxOvers = spells.maxOfOrNull { it.endOver } ?: 20

                    // Draw timeline
                    drawLine(
                        color = Color(0xFF3D3D55),
                        start = Offset(40f, height / 2),
                        end = Offset(width - 20f, height / 2),
                        strokeWidth = 3f,
                    )

                    // Draw spell blocks
                    spells.forEachIndexed { index, spell ->
                        val startX = 40f + (spell.startOver.toFloat() / maxOvers) * (width - 60f)
                        val endX = 40f + (spell.endOver.toFloat() / maxOvers) * (width - 60f)
                        val spellWidth = (endX - startX) * animatedProgress.value

                        // Economy color for spell
                        val economy = if (spell.overs.isNotEmpty()) {
                            val oversFloat = spell.overs.replace("-", ".").toFloatOrNull() ?: 1f
                            spell.runs / (if (oversFloat > 0) oversFloat else 1f)
                        } else {
                            6f
                        }

                        val spellColor = when {
                            economy <= 4f -> colors.statusSuccess
                            economy <= 6f -> colors.statusInfo
                            economy <= 8f -> colors.statusWarning
                            else -> colors.statusLive
                        }

                        // Draw spell block
                        val blockHeight = 40f + (spell.wickets * 15f)
                        drawRoundRect(
                            color = spellColor.copy(alpha = 0.7f),
                            topLeft = Offset(startX, height / 2 - blockHeight / 2),
                            size = androidx.compose.ui.geometry.Size(spellWidth, blockHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                        )

                        // Draw wicket markers
                        if (spell.wickets > 0 && animatedProgress.value > 0.5f) {
                            for (w in 0 until spell.wickets) {
                                val wicketX = startX + spellWidth / 2
                                val wicketY = height / 2 - blockHeight / 2 - 10f - (w * 12f)

                                drawCircle(
                                    color = colors.statusLive,
                                    radius = 5f,
                                    center = Offset(wicketX, wicketY),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Spell legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                LegendItem(color = colors.statusSuccess, text = "Econ < 4")
                LegendItem(color = colors.statusInfo, text = "Econ 4-6")
                LegendItem(color = colors.statusWarning, text = "Econ 6-8")
                LegendItem(color = colors.statusLive, text = "Econ > 8")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Spell details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Spell",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.width(50.dp),
                    )
                    Text(
                        text = "Overs",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.width(50.dp),
                    )
                    Text(
                        text = "M",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        text = "R",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        text = "W",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        text = "Dots",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.width(40.dp),
                    )
                    Text(
                        text = "Econ",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.width(40.dp),
                    )
                }

                HorizontalDivider(color = Color(0xFF3D3D55), thickness = 1.dp)

                spells.forEach { spell ->
                    val economy = if (spell.overs.isNotEmpty()) {
                        val oversFloat = spell.overs.replace("-", ".").toFloatOrNull() ?: 1f
                        if (oversFloat > 0) spell.runs / oversFloat else 0f
                    } else {
                        0f
                    }

                    val economyColor = when {
                        economy <= 4f -> colors.statusSuccess
                        economy <= 6f -> colors.statusInfo
                        economy <= 8f -> colors.statusWarning
                        else -> colors.statusLive
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "#${spell.spellNumber}",
                            color = colors.goldDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(50.dp),
                        )
                        Text(
                            text = spell.overs,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.width(50.dp),
                        )
                        Text(
                            text = "${spell.maidens}",
                            color = if (spell.maidens > 0) colors.statusSuccess else Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.width(30.dp),
                        )
                        Text(
                            text = "${spell.runs}",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.width(30.dp),
                        )
                        Text(
                            text = "${spell.wickets}",
                            color = if (spell.wickets > 0) colors.statusLive else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (spell.wickets > 0) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.width(30.dp),
                        )
                        Text(
                            text = "${spell.dots}",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.width(40.dp),
                        )
                        Text(
                            text = String.format("%.1f", economy),
                            color = economyColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Best spell callout
            val bestSpell = spells.maxByOrNull { it.wickets * 100 - it.runs }
            bestSpell?.let { spell ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    colors.statusSuccess.copy(alpha = 0.2f),
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
                            text = "BEST SPELL",
                            color = colors.statusSuccess,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = "Spell #${spell.spellNumber} (Overs ${spell.startOver}-${spell.endOver})",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Text(
                        text = "${spell.wickets}/${spell.runs}",
                        color = colors.statusSuccess,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    text: String,
) {
    val colors = BouncerDesignSystem.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = Color.Gray,
            fontSize = 10.sp,
        )
    }
}

@Composable
fun SpellAnalysisCardPreview() {
    val colors = BouncerDesignSystem.colors
    val spells = listOf(
        BowlerSpell(1, "4", 1, 18, 2, 14, 2, 1, 4),
        BowlerSpell(2, "3", 0, 24, 0, 8, 4, 8, 11),
        BowlerSpell(3, "3", 1, 12, 1, 11, 1, 15, 18),
    )

    SpellAnalysisCard(
        bowlerName = "Jasprit Bumrah",
        spells = spells,
        totalOvers = "10",
        totalWickets = 3,
        totalRuns = 54,
    )
}

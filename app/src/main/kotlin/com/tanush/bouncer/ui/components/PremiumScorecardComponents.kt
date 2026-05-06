package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Premium batting scorecard data
 */
data class BatsmanScoreData(
    val name: String,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Float,
    val dismissal: String, // "not out", "c Player b Bowler", etc.
    val isNotOut: Boolean = false,
    val isOnStrike: Boolean = false
)

/**
 * Premium bowling scorecard data
 */
data class BowlerScoreData(
    val name: String,
    val overs: Float,
    val maidens: Int,
    val runs: Int,
    val wickets: Int,
    val economy: Float,
    val dots: Int = 0,
    val isBowling: Boolean = false
)

/**
 * Fall of wicket data
 */
data class FallOfWicketData(
    val wicketNumber: Int,
    val score: Int,
    val batsmanName: String,
    val overNumber: Float
)

/**
 * Premium Batting Scorecard Component
 */
@Composable
fun PremiumBattingScorecard(
    teamName: String,
    teamColor: Color,
    batsmen: List<BatsmanScoreData>,
    extras: Int,
    total: String,
    overs: String,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    Card(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = teamColor.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.3f)
            ),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(0.5.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        teamColor.copy(alpha = 0.3f),
                                        teamColor.copy(alpha = 0.1f)
                                    )
                                ),
                                CircleShape
                            )
                            .border(1.dp, teamColor.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SportsCricket,
                            contentDescription = null,
                            tint = teamColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            teamName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            "Batting",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        total,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = teamColor
                    )
                    Text(
                        "($overs ov)",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Column Headers
            ScorecardHeader()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            HorizontalDivider(
                color = hierarchy.secondary.primary.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            
            // Batsmen rows
            batsmen.forEachIndexed { index, batsman ->
                val delayedVisible by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 500,
                        delayMillis = 50 * index,
                        easing = FastOutSlowInEasing
                    ),
                    label = "batsman_$index"
                )
                
                BatsmanRow(
                    batsman = batsman,
                    teamColor = teamColor,
                    animationProgress = delayedVisible
                )
                
                if (index < batsmen.lastIndex) {
                    HorizontalDivider(
                        color = GlassBorderLight,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Extras row with glass surface background
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        GlassBackgroundHover,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Extras",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
                Text(
                    "$extras",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Total row with gold accent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TOTAL",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = hierarchy.secondary.primary,
                    letterSpacing = 2.sp
                )
                Text(
                    total,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gold gradient divider (between batting and bowling sections)
            GoldGradientDivider()
        }
    }
}

/**
 * Gold gradient divider — horizontal separator matching the mockup's
 * "from-transparent via-primary/30 to-transparent" effect.
 */
@Composable
fun GoldGradientDivider(modifier: Modifier = Modifier) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        hierarchy.secondary.primary.copy(alpha = 0.30f),
                        Color.Transparent
                    )
                )
            )
    )
}

// Column header text — single per-column source of truth used by ScorecardHeader/BowlingHeader.
@Composable
private fun RowScope.HeaderCell(
    label: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Center,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.5.sp
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = hierarchy.secondary.primary.copy(alpha = 0.6f),
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        modifier = modifier
    )
}

@Composable
private fun ScorecardHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("BATTER", Modifier.weight(1f), TextAlign.Start, letterSpacing = 1.sp)
        HeaderCell("R", Modifier.width(32.dp))
        HeaderCell("B", Modifier.width(32.dp))
        HeaderCell("4s", Modifier.width(28.dp))
        HeaderCell("6s", Modifier.width(28.dp))
        HeaderCell("S/R", Modifier.width(48.dp), TextAlign.End)
    }
}

@Composable
private fun BatsmanRow(
    batsman: BatsmanScoreData,
    teamColor: Color,
    animationProgress: Float
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val isHighScore = batsman.runs >= 50
    val isCentury = batsman.runs >= 100
    
    val rowAlpha = animationProgress
    val slideOffset = (1f - animationProgress) * 20f
    
    // Capture for drawBehind scope
    val goldPrimaryColor = hierarchy.secondary.primary

    // Strike-row: gold inset left border + subtle team-tinted gradient background
    val strikeRowModifier = if (batsman.isNotOut) {
        Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        teamColor.copy(alpha = 0.10f),
                        teamColor.copy(alpha = 0.02f)
                    )
                ),
                RoundedCornerShape(4.dp)
            )
            .drawBehind {
                // Gold inset left border (3dp wide)
                drawRect(
                    color = goldPrimaryColor,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height)
                )
            }
    } else {
        Modifier.fillMaxWidth()
    }

    Row(
        modifier = strikeRowModifier
            .graphicsLayer {
                alpha = rowAlpha
                translationX = slideOffset
            }
            .padding(
                start = if (batsman.isNotOut) 12.dp else 0.dp,
                end = 0.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    batsman.name,
                    fontSize = 14.sp,
                    fontWeight = if (batsman.isNotOut) FontWeight.Bold else FontWeight.Medium,
                    color = if (batsman.isNotOut) colors.textPrimary else colors.textPrimary.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (batsman.isOnStrike) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.secondary)
                                ),
                                CircleShape
                            )
                    )
                }
                
                if (isCentury) {
                    Spacer(modifier = Modifier.width(6.dp))
                    MilestoneBadge("100", NeonSuccess)
                } else if (isHighScore) {
                    Spacer(modifier = Modifier.width(6.dp))
                    MilestoneBadge("50", hierarchy.secondary.primary)
                }
            }
            
            if (!batsman.isNotOut && batsman.dismissal.isNotEmpty()) {
                Text(
                    batsman.dismissal,
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (batsman.isNotOut) {
                // Pill-shaped "not out" status badge
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    color = NeonSuccess.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(0.5.dp, NeonSuccess.copy(alpha = 0.25f))
                ) {
                    Text(
                        "not out",
                        fontSize = 9.sp,
                        color = NeonSuccess.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        
        // Stats
        Text(
            "${batsman.runs}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighScore) teamColor else colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Text(
            "${batsman.balls}",
            fontSize = 13.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Text(
            "${batsman.fours}",
            fontSize = 13.sp,
            color = if (batsman.fours > 0) BoundaryBlue else colors.textTertiary,
            fontWeight = if (batsman.fours > 0) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Text(
            "${batsman.sixes}",
            fontSize = 13.sp,
            color = if (batsman.sixes > 0) SixerPurple else colors.textTertiary,
            fontWeight = if (batsman.sixes > 0) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Text(
            String.format("%.1f", batsman.strikeRate),
            fontSize = 13.sp,
            color = when {
                batsman.strikeRate >= 150 -> NeonSuccess
                batsman.strikeRate >= 100 -> colors.textPrimary
                else -> colors.textSecondary
            },
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
private fun MilestoneBadge(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

/**
 * Premium Bowling Scorecard Component
 */
@Composable
fun PremiumBowlingScorecard(
    teamName: String,
    teamColor: Color,
    bowlers: List<BowlerScoreData>,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    Card(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = teamColor.copy(alpha = 0.12f),
                ambientColor = Color.Black.copy(alpha = 0.25f)
            ),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            teamColor.copy(alpha = 0.15f),
                            CircleShape
                        )
                        .border(0.5.dp, teamColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Adjust,
                        contentDescription = null,
                        tint = teamColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Bowling",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Column Headers
            BowlingHeader()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            HorizontalDivider(
                color = hierarchy.secondary.primary.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            
            // Bowler rows
            bowlers.forEachIndexed { index, bowler ->
                val delayedVisible by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 500,
                        delayMillis = 50 * index,
                        easing = FastOutSlowInEasing
                    ),
                    label = "bowler_$index"
                )
                
                BowlerRow(
                    bowler = bowler,
                    teamColor = teamColor,
                    animationProgress = delayedVisible
                )
                
                if (index < bowlers.lastIndex) {
                    HorizontalDivider(
                        color = GlassBorderLight,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BowlingHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("BOWLER", Modifier.weight(1f), TextAlign.Start, letterSpacing = 1.sp)
        HeaderCell("O", Modifier.width(32.dp))
        HeaderCell("M", Modifier.width(28.dp))
        HeaderCell("R", Modifier.width(32.dp))
        HeaderCell("W", Modifier.width(28.dp))
        HeaderCell("ECON", Modifier.width(44.dp), TextAlign.End)
    }
}

@Composable
private fun BowlerRow(
    bowler: BowlerScoreData,
    teamColor: Color,
    animationProgress: Float
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val isGoodFigures = bowler.wickets >= 3
    val isFiveWicket = bowler.wickets >= 5

    // Subtle glass surface highlight for current bowler
    val rowBackground = if (bowler.isBowling) {
        Modifier.background(GlassBackgroundHover, RoundedCornerShape(4.dp))
    } else {
        Modifier
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowBackground)
            .graphicsLayer {
                alpha = animationProgress
                translationX = (1f - animationProgress) * 20f
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                bowler.name,
                fontSize = 14.sp,
                fontWeight = if (bowler.isBowling) FontWeight.Bold else FontWeight.Medium,
                color = if (bowler.isBowling) colors.textPrimary else colors.textPrimary.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (bowler.isBowling) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.secondary)
                            ),
                            CircleShape
                        )
                )
            }
            
            if (isFiveWicket) {
                Spacer(modifier = Modifier.width(6.dp))
                MilestoneBadge("5W", NeonSuccess)
            } else if (isGoodFigures) {
                Spacer(modifier = Modifier.width(6.dp))
                MilestoneBadge("3W", hierarchy.secondary.primary)
            }
        }
        
        Text(
            String.format("%.1f", bowler.overs),
            fontSize = 13.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Text(
            "${bowler.maidens}",
            fontSize = 13.sp,
            color = if (bowler.maidens > 0) NeonSuccess else colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Text(
            "${bowler.runs}",
            fontSize = 13.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        // Wickets — gold-colored per mockup
        Text(
            "${bowler.wickets}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                isFiveWicket -> NeonSuccess
                bowler.wickets > 0 -> hierarchy.secondary.primary
                else -> colors.textTertiary
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Text(
            String.format("%.1f", bowler.economy),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                bowler.economy <= 6 -> NeonSuccess
                bowler.economy <= 8 -> colors.textPrimary
                bowler.economy <= 10 -> hierarchy.secondary.primary
                else -> NeonError
            },
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp)
        )
    }
}

/**
 * Fall of Wickets Timeline Component
 */
@Composable
fun FallOfWicketsTimeline(
    fallOfWickets: List<FallOfWicketData>,
    teamColor: Color,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val glassBorderColor = colors.glassBorder
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    Card(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = teamColor.copy(alpha = 0.1f)
            ),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Timeline,
                    contentDescription = null,
                    tint = hierarchy.secondary.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Fall of Wickets",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Timeline visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val lineY = size.height * 0.5f
                    
                    // Draw timeline line
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                glassBorderColor.copy(alpha = 0.2f),
                                teamColor.copy(alpha = 0.5f),
                                glassBorderColor.copy(alpha = 0.2f)
                            )
                        ),
                        start = Offset(0f, lineY),
                        end = Offset(size.width, lineY),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                
                // Wicket markers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    fallOfWickets.forEachIndexed { index, fow ->
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isVisible) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = 80 * index,
                                easing = FastOutSlowInEasing
                            ),
                            label = "wicket_$index"
                        )
                        
                        WicketMarker(
                            fow = fow,
                            teamColor = teamColor,
                            scale = animatedScale
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WicketMarker(
    fow: FallOfWicketData,
    teamColor: Color,
    scale: Float
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = scale
        }
    ) {
        Text(
            "${fow.score}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            teamColor.copy(alpha = 0.4f),
                            teamColor.copy(alpha = 0.1f)
                        )
                    ),
                    CircleShape
                )
                .border(1.5.dp, teamColor.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${fow.wicketNumber}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = teamColor
            )
        }
        
        Text(
            "${fow.overNumber}",
            fontSize = 10.sp,
            color = colors.textTertiary
        )
    }
}

/**
 * Extras Breakdown Component
 */
@Composable
fun ExtrasBreakdown(
    wides: Int,
    noBalls: Int,
    byes: Int,
    legByes: Int,
    penalty: Int = 0,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val total = wides + noBalls + byes + legByes + penalty
    
    Card(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.08f)
            ),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtraItem(label = "Wd", value = wides, color = hierarchy.secondary.primary)
            ExtraItem(label = "Nb", value = noBalls, color = NeonError)
            ExtraItem(label = "B", value = byes, color = colors.textSecondary)
            ExtraItem(label = "Lb", value = legByes, color = colors.textSecondary)
            
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .width(1.dp)
                    .background(colors.glassBorder.copy(alpha = 0.3f))
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "$total",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary
                )
                Text(
                    "Total",
                    fontSize = 10.sp,
                    color = colors.textTertiary
                )
            }
        }
    }
}

@Composable
private fun ExtraItem(
    label: String,
    value: Int,
    color: Color
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "$value",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (value > 0) color else colors.textTertiary
        )
        Text(
            label,
            fontSize = 10.sp,
            color = colors.textTertiary
        )
    }
}

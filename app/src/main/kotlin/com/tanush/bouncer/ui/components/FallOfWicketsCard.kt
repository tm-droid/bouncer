package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.*
import kotlin.math.abs
import io.sentry.Sentry
import com.tanush.bouncer.ui.theme.GoldPrimary
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Fall of wicket data
 */
data class FallOfWicket(
    val wicketNumber: Int,
    val score: Int,
    val overs: Float,
    val batsmanName: String,
    val batsmanRuns: Int,
    val batsmanBalls: Int,
    val bowlerName: String,
    val dismissalType: DismissalType,
    val partnershipRuns: Int = 0
)

/**
 * Types of dismissals
 */
enum class DismissalType(val label: String, val shortLabel: String) {
    BOWLED("Bowled", "b"),
    CAUGHT("Caught", "c"),
    LBW("LBW", "lbw"),
    RUN_OUT("Run Out", "run out"),
    STUMPED("Stumped", "st"),
    HIT_WICKET("Hit Wicket", "hit wkt"),
    CAUGHT_BEHIND("Caught Behind", "c †"),
    CAUGHT_AND_BOWLED("Caught & Bowled", "c&b")
}

/**
 * Premium Fall of Wickets Card
 * Shows timeline of wickets with detailed analysis
 */
@Composable
fun FallOfWicketsCard(
    wickets: List<FallOfWicket>,
    teamName: String,
    currentScore: Int,
    currentOvers: Float,
    teamColor: Color = GoldPrimary,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 0.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = colors.statusLive.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = BouncerThemeV2.colors.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, BouncerThemeV2.colors.divider)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = colors.statusLive,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "FALL OF WICKETS",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BouncerThemeV2.colors.surfaceVariant,
                    border = BorderStroke(1.dp, BouncerThemeV2.colors.divider)
                ) {
                    Text(
                        text = teamName,
                        color = teamColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Visual timeline
            WicketTimeline(
                wickets = wickets,
                currentScore = currentScore,
                currentOvers = currentOvers,
                teamColor = teamColor
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Scrollable wicket cards
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                itemsIndexed(wickets, key = { index, _ -> index }) { index, wicket ->
                    WicketCard(
                        wicket = wicket,
                        isLatest = index == wickets.size - 1,
                        teamColor = teamColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Summary stats
            WicketsSummary(wickets = wickets, currentScore = currentScore)
        }
    }
}

/**
 * Visual timeline showing wicket fall pattern
 */
@Composable
private fun WicketTimeline(
    wickets: List<FallOfWicket>,
    currentScore: Int,
    currentOvers: Float,
    teamColor: Color
) {
    val animatedProgress = remember { Animatable(0f) }
    
    LaunchedEffect(wickets) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing)
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val baselineColor = GlassBorder
        val gridColor = GlassBorder
        Canvas(modifier = Modifier.fillMaxSize()) {
            val chartWidth = this.size.width
            val chartHeight = this.size.height - 20.dp.toPx()
            val maxScore = currentScore.coerceAtLeast((wickets.maxOfOrNull { it.score } ?: 100) + 20)
            
            // Draw background gradient
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        StatusLive.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                ),
                cornerRadius = CornerRadius(12.dp.toPx())
            )
            
            // Draw score zones (0-50, 50-100, etc.)
            val zones = (0..maxScore step 50).toList()
            zones.forEach { score ->
                val y = chartHeight - (score.toFloat() / maxScore) * chartHeight
                drawLine(
                    color = gridColor.copy(alpha = 0.2f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                )
            }
            
            // Draw wicket line (connecting wickets)
            if (wickets.isNotEmpty()) {
                val path = Path()
                val displayedWickets = wickets.take((wickets.size * animatedProgress.value).toInt().coerceAtLeast(1))
                
                displayedWickets.forEachIndexed { index, wicket ->
                    val x = (wicket.wicketNumber.toFloat() / 10f) * chartWidth * 0.9f + chartWidth * 0.05f
                    val y = chartHeight - (wicket.score.toFloat() / maxScore) * chartHeight
                    
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                
                // Draw line shadow
                drawPath(
                    path = path,
                    color = StatusLive.copy(alpha = 0.2f),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                
                // Draw main line
                drawPath(
                    path = path,
                    color = StatusLive,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                
                // Draw wicket dots
                displayedWickets.forEach { wicket ->
                    val x = (wicket.wicketNumber.toFloat() / 10f) * chartWidth * 0.9f + chartWidth * 0.05f
                    val y = chartHeight - (wicket.score.toFloat() / maxScore) * chartHeight
                    
                    // Outer glow
                    drawCircle(
                        color = StatusLive.copy(alpha = 0.3f),
                        radius = 10.dp.toPx(),
                        center = Offset(x, y)
                    )
                    
                    // Dot
                    drawCircle(
                        color = StatusLive,
                        radius = 5.dp.toPx(),
                        center = Offset(x, y)
                    )
                    
                    // Inner white dot
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            
            // Baseline
            drawLine(
                color = baselineColor.copy(alpha = 0.3f),
                start = Offset(0f, chartHeight),
                end = Offset(chartWidth, chartHeight),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        // Wicket number labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            (1..10).forEach { num ->
                Text(
                    text = "$num",
                    color = if (num <= wickets.size) StatusLive else TextTertiary.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontWeight = if (num <= wickets.size) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Individual wicket card
 */
@Composable
private fun WicketCard(
    wicket: FallOfWicket,
    isLatest: Boolean,
    teamColor: Color
) {
    val colors = BouncerDesignSystem.colors
    // Ambient loop removed: motion in this app reports a state change, never idles.
    // See BouncerCard's docs for the rule.
    val borderAlpha = 0.55f
    
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isLatest) colors.statusLive.copy(alpha = 0.1f) else BouncerThemeV2.colors.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isLatest) colors.statusLive.copy(alpha = borderAlpha) else BouncerThemeV2.colors.divider
        ),
        modifier = Modifier.width(160.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Wicket number and score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.statusLive.copy(alpha = 0.2f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${wicket.wicketNumber}",
                            color = colors.statusLive,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${wicket.score}",
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "(${wicket.overs} ov)",
                        color = colors.textTertiary,
                        fontSize = 10.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BouncerThemeV2.colors.divider)
            Spacer(modifier = Modifier.height(10.dp))
            
            // Batsman info
            Text(
                text = wicket.batsmanName,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${wicket.batsmanRuns} (${wicket.batsmanBalls})",
                    color = teamColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                
                // Dismissal type badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = getDismissalColor(wicket.dismissalType).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = wicket.dismissalType.shortLabel,
                        color = getDismissalColor(wicket.dismissalType),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Bowler
            Text(
                text = wicket.bowlerName,
                color = colors.textTertiary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Get color for dismissal type
 */
@Composable
@ReadOnlyComposable
private fun getDismissalColor(type: DismissalType): Color = when (type) {
    DismissalType.BOWLED -> StatusLive
    DismissalType.LBW -> StatusInfo
    DismissalType.CAUGHT, DismissalType.CAUGHT_BEHIND, DismissalType.CAUGHT_AND_BOWLED -> GoldPrimary
    DismissalType.RUN_OUT -> StatusWarning
    DismissalType.STUMPED -> StatusSuccess
    DismissalType.HIT_WICKET -> TextTertiary
}

/**
 * Wickets summary
 */
@Composable
private fun WicketsSummary(wickets: List<FallOfWicket>, currentScore: Int) {
    val colors = BouncerDesignSystem.colors
    val mostCommonDismissal = wickets.groupBy { it.dismissalType }
        .maxByOrNull { it.value.size }
    val avgPartnership = if (wickets.isNotEmpty()) {
        wickets.sumOf { it.partnershipRuns } / wickets.size
    } else 0
    val topScorer = wickets.maxByOrNull { it.batsmanRuns }
    val collapsePhase = findCollapsePhase(wickets)
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BouncerThemeV2.colors.surfaceVariant,
        border = BorderStroke(0.5.dp, BouncerThemeV2.colors.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${wickets.size}",
                        color = colors.statusLive,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = stringResource(R.string.common_wickets),
                        color = colors.textTertiary,
                        fontSize = 9.sp
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$avgPartnership",
                        color = colors.statusInfo,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = stringResource(R.string.fow_avg_partnership),
                        color = colors.textTertiary,
                        fontSize = 9.sp
                    )
                }
                
                mostCommonDismissal?.let { (type, list) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${list.size}",
                            color = getDismissalColor(type),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = type.label,
                            color = colors.textTertiary,
                            fontSize = 9.sp
                        )
                    }
                }
            }
            
            // Collapse warning if detected
            collapsePhase?.let { (start, end, wicketsLost) ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BouncerThemeV2.colors.divider)
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = colors.statusWarning,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Collapse: Lost $wicketsLost wkts between $start-$end",
                        color = colors.statusWarning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Find collapse phase (3+ wickets in 20 runs or less)
 */
private fun findCollapsePhase(wickets: List<FallOfWicket>): Triple<Int, Int, Int>? {
    try {
        if (wickets.size < 3) return null
    
        for (i in 0 until wickets.size - 2) {
            val startScore = if (i == 0) 0 else wickets[i - 1].score
            for (j in i + 2 until wickets.size) {
                val wicketsLost = j - i + 1
                val runsScored = wickets[j].score - startScore
                if (wicketsLost >= 3 && runsScored <= 20) {
                    return Triple(startScore, wickets[j].score, wicketsLost)
                }
            }
        }
        return null
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * Compact FOW display
 */
@Composable
fun CompactFOWDisplay(
    wickets: List<FallOfWicket>,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        wickets.forEach { wicket ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colors.statusLive.copy(alpha = 0.1f),
                border = BorderStroke(0.5.dp, colors.statusLive.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${wicket.score}",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${wicket.wicketNumber}",
                        color = colors.statusLive,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

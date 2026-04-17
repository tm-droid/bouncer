package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Player comparison data
 */
data class ComparisonPlayer(
    val name: String,
    val teamName: String,
    val imageUrl: String? = null,
    val role: String,
    val stats: Map<String, ComparisonStat>
)

data class ComparisonStat(
    val value: Float,
    val displayValue: String,
    val isHigherBetter: Boolean = true
)

/**
 * Premium Player Comparison Card
 * Side by side comparison of two players
 */
@Composable
fun PlayerComparisonCard(
    player1: ComparisonPlayer,
    player2: ComparisonPlayer,
    statCategories: List<String>,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "comparison")
    
    val vsGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vsGlow"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = null,
                    tint = hierarchy.secondary.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "PLAYER COMPARISON",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Player profiles with VS
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Player 1
                PlayerProfile(
                    player = player1,
                    color = hierarchy.secondary.primary,
                    modifier = Modifier.weight(1f)
                )
                
                // VS badge
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .size(44.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    hierarchy.secondary.primary.copy(alpha = vsGlow * 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = GlassSurface,
                        border = BorderStroke(1.dp, hierarchy.secondary.primary.copy(alpha = vsGlow)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "VS",
                                color = hierarchy.secondary.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                
                // Player 2
                PlayerProfile(
                    player = player2,
                    color = colors.statusInfo,
                    modifier = Modifier.weight(1f),
                    alignEnd = true
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Stats comparison
            statCategories.forEach { category ->
                val stat1 = player1.stats[category]
                val stat2 = player2.stats[category]
                
                if (stat1 != null && stat2 != null) {
                    ComparisonStatRow(
                        statName = category,
                        player1Value = stat1.value,
                        player1Display = stat1.displayValue,
                        player2Value = stat2.value,
                        player2Display = stat2.displayValue,
                        isHigherBetter = stat1.isHigherBetter,
                        player1Color = hierarchy.secondary.primary,
                        player2Color = colors.statusInfo
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Verdict
            ComparisonVerdict(player1 = player1, player2 = player2, stats = statCategories)
        }
    }
}

/**
 * Player profile section
 */
@Composable
private fun PlayerProfile(
    player: ComparisonPlayer,
    color: Color,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        // Avatar
        if (player.imageUrl != null) {
            CoilImage(
                model = player.imageUrl,
                contentDescription = player.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .border(2.dp, color.copy(alpha = 0.5f), CircleShape)
                    .align(if (alignEnd) Alignment.End else Alignment.Start)
            )
        } else {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
                border = BorderStroke(2.dp, color.copy(alpha = 0.5f)),
                modifier = Modifier
                    .size(60.dp)
                    .align(if (alignEnd) Alignment.End else Alignment.Start)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = player.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                        color = color,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = player.name,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(
            text = player.teamName,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.align(if (alignEnd) Alignment.End else Alignment.Start)
        ) {
            Text(
                text = player.role,
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Comparison stat row with visual bar
 */
@Composable
private fun ComparisonStatRow(
    statName: String,
    player1Value: Float,
    player1Display: String,
    player2Value: Float,
    player2Display: String,
    isHigherBetter: Boolean,
    player1Color: Color,
    player2Color: Color
) {
    val colors = BouncerDesignSystem.colors
    val maxValue = maxOf(player1Value, player2Value).coerceAtLeast(0.01f)
    val player1Ratio = player1Value / maxValue
    val player2Ratio = player2Value / maxValue
    
    val player1Wins = if (isHigherBetter) player1Value >= player2Value else player1Value <= player2Value
    val player2Wins = if (isHigherBetter) player2Value > player1Value else player2Value < player1Value
    
    // Animated widths
    val animatedPlayer1 by animateFloatAsState(
        targetValue = player1Ratio,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "player1"
    )
    val animatedPlayer2 by animateFloatAsState(
        targetValue = player2Ratio,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "player2"
    )
    
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = GlassSurface.copy(alpha = 0.3f),
        border = BorderStroke(0.5.dp, colors.glassBorder.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Stat name
            Text(
                text = statName,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Values and bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Player 1 value
                Text(
                    text = player1Display,
                    color = if (player1Wins) player1Color else colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (player1Wins) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.width(50.dp)
                )
                
                // Bars
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Player 1 bar (grows from center to left)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedPlayer1)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            if (player1Wins) player1Color.copy(alpha = 0.3f) else colors.glassBorder.copy(alpha = 0.2f),
                                            if (player1Wins) player1Color else colors.glassBorder
                                        )
                                    )
                                )
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Player 2 bar (grows from center to right)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedPlayer2)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            if (player2Wins) player2Color else colors.glassBorder,
                                            if (player2Wins) player2Color.copy(alpha = 0.3f) else colors.glassBorder.copy(alpha = 0.2f)
                                        )
                                    )
                                )
                        )
                    }
                }
                
                // Player 2 value
                Text(
                    text = player2Display,
                    color = if (player2Wins) player2Color else colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (player2Wins) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(50.dp)
                )
            }
        }
    }
}

/**
 * Comparison verdict
 */
@Composable
private fun ComparisonVerdict(
    player1: ComparisonPlayer,
    player2: ComparisonPlayer,
    stats: List<String>
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    var player1Wins = 0
    var player2Wins = 0
    
    stats.forEach { stat ->
        val s1 = player1.stats[stat]
        val s2 = player2.stats[stat]
        if (s1 != null && s2 != null) {
            val p1Better = if (s1.isHigherBetter) s1.value > s2.value else s1.value < s2.value
            val p2Better = if (s1.isHigherBetter) s2.value > s1.value else s2.value < s1.value
            if (p1Better) player1Wins++
            if (p2Better) player2Wins++
        }
    }
    
    val verdict = when {
        player1Wins > player2Wins -> player1.name to hierarchy.secondary.primary
        player2Wins > player1Wins -> player2.name to colors.statusInfo
        else -> "Draw" to colors.textTertiary
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = verdict.second.copy(alpha = 0.1f),
        border = BorderStroke(0.5.dp, verdict.second.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (verdict.first != "Draw") {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = verdict.second,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Text(
                text = if (verdict.first == "Draw") "It's a Draw! ($player1Wins - $player2Wins)" 
                       else "${verdict.first} leads $player1Wins - $player2Wins",
                color = verdict.second,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Mini comparison indicator
 */
@Composable
fun MiniComparisonIndicator(
    value1: Float,
    value2: Float,
    color1: Color = BouncerDesignSystem.hierarchy.secondary.primary,
    color2: Color = BouncerDesignSystem.colors.statusInfo,
    modifier: Modifier = Modifier.width(60.dp)
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val total = (value1 + value2).coerceAtLeast(0.01f)
    val ratio1 = value1 / total
    
    Row(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(ratio1.coerceIn(0.05f, 0.95f))
                .background(color1)
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight((1f - ratio1).coerceIn(0.05f, 0.95f))
                .background(color2)
        )
    }
}

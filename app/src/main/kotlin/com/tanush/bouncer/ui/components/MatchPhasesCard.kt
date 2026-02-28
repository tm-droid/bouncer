package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Match phase data
 */
data class MatchPhase(
    val phaseName: String,
    val description: String,
    val overs: String,
    val team1Runs: Int,
    val team1Wickets: Int,
    val team2Runs: Int? = null,
    val team2Wickets: Int? = null,
    val keyEvent: String? = null,
    val momentumShift: MomentumShift? = null,
    val isActive: Boolean = false
)

enum class MomentumShift {
    TEAM1_STRONG,
    TEAM2_STRONG,
    BALANCED
}

/**
 * Premium Match Phases Card
 * Shows timeline of match phases with momentum shifts
 */
@Composable
fun MatchPhasesCard(
    phases: List<MatchPhase>,
    team1Name: String,
    team2Name: String,
    team1Color: Color = ShinyGoldDeep,
    team2Color: Color = ChartBlue,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = ShinyGoldDeep.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.3f))
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
                        Icons.Default.Timeline,
                        contentDescription = null,
                        tint = ShinyGoldDeep,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "MATCH PHASES",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                // Team legend
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TeamIndicator(name = team1Name, color = team1Color)
                    TeamIndicator(name = team2Name, color = team2Color)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Phases timeline
            phases.forEachIndexed { index, phase ->
                PhaseRow(
                    phase = phase,
                    phaseNumber = index + 1,
                    team1Name = team1Name,
                    team2Name = team2Name,
                    team1Color = team1Color,
                    team2Color = team2Color,
                    isLast = index == phases.size - 1
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Momentum summary
            MomentumSummary(
                phases = phases,
                team1Name = team1Name,
                team2Name = team2Name,
                team1Color = team1Color,
                team2Color = team2Color
            )
        }
    }
}

/**
 * Team indicator
 */
@Composable
private fun TeamIndicator(name: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = name.take(6),
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

/**
 * Phase row
 */
@Composable
private fun PhaseRow(
    phase: MatchPhase,
    phaseNumber: Int,
    team1Name: String,
    team2Name: String,
    team1Color: Color,
    team2Color: Color,
    isLast: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "phase")
    
    val activeGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    val phaseColor = when (phase.momentumShift) {
        MomentumShift.TEAM1_STRONG -> team1Color
        MomentumShift.TEAM2_STRONG -> team2Color
        else -> TextTertiary
    }
    
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Timeline indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            // Phase dot
            Box(
                modifier = Modifier
                    .size(if (phase.isActive) 20.dp else 16.dp)
                    .then(
                        if (phase.isActive) {
                            Modifier.background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        phaseColor.copy(alpha = activeGlow),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (phase.isActive) phaseColor else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(2.dp, phaseColor),
                    modifier = Modifier.size(if (phase.isActive) 16.dp else 12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (phase.isActive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }
                }
            }
            
            // Connecting line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(60.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(phaseColor, phaseColor.copy(alpha = 0.3f))
                            )
                        )
                )
            }
        }
        
        // Phase content
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (phase.isActive) phaseColor.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.08f),
            border = BorderStroke(
                0.5.dp,
                if (phase.isActive) phaseColor.copy(alpha = 0.5f) else CardBorder.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, bottom = if (isLast) 0.dp else 12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Phase header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = phase.phaseName,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (phase.isActive) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SuccessGreen.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        color = SuccessGreen,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Overs ${phase.overs}",
                            color = TextTertiary,
                            fontSize = 10.sp
                        )
                    }
                    
                    // Momentum indicator
                    phase.momentumShift?.let { shift ->
                        MomentumIndicator(shift = shift, team1Color = team1Color, team2Color = team2Color)
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Scores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Team 1 score
                    ScoreBox(
                        teamName = team1Name,
                        runs = phase.team1Runs,
                        wickets = phase.team1Wickets,
                        color = team1Color
                    )
                    
                    // VS
                    Text(
                        text = "vs",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                    
                    // Team 2 score
                    if (phase.team2Runs != null) {
                        ScoreBox(
                            teamName = team2Name,
                            runs = phase.team2Runs,
                            wickets = phase.team2Wickets ?: 0,
                            color = team2Color
                        )
                    } else {
                        Text(
                            text = "Yet to bat",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
                
                // Key event
                phase.keyEvent?.let { event ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = ShinyGoldDeep,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = event,
                            color = ShinyGoldDeep,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Score box
 */
@Composable
private fun ScoreBox(teamName: String, runs: Int, wickets: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = teamName.take(6),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "$runs/$wickets",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Momentum indicator
 */
@Composable
private fun MomentumIndicator(
    shift: MomentumShift,
    team1Color: Color,
    team2Color: Color
) {
    val (icon, color) = when (shift) {
        MomentumShift.TEAM1_STRONG -> Icons.AutoMirrored.Filled.TrendingUp to team1Color
        MomentumShift.TEAM2_STRONG -> Icons.AutoMirrored.Filled.TrendingUp to team2Color
        MomentumShift.BALANCED -> Icons.Default.Balance to TextTertiary
    }
    
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .padding(4.dp)
                .size(14.dp)
        )
    }
}

/**
 * Momentum summary
 */
@Composable
private fun MomentumSummary(
    phases: List<MatchPhase>,
    team1Name: String,
    team2Name: String,
    team1Color: Color,
    team2Color: Color
) {
    val team1Phases = phases.count { it.momentumShift == MomentumShift.TEAM1_STRONG }
    val team2Phases = phases.count { it.momentumShift == MomentumShift.TEAM2_STRONG }
    val balancedPhases = phases.count { it.momentumShift == MomentumShift.BALANCED }
    
    val leader = when {
        team1Phases > team2Phases -> team1Name to team1Color
        team2Phases > team1Phases -> team2Name to team2Color
        else -> "Balanced" to TextTertiary
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "MOMENTUM SUMMARY",
                color = TextTertiary,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            
            // Phase breakdown bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                if (team1Phases > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(team1Phases.toFloat())
                            .background(team1Color)
                    )
                }
                if (balancedPhases > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(balancedPhases.toFloat())
                            .background(TextTertiary)
                    )
                }
                if (team2Phases > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(team2Phases.toFloat())
                            .background(team2Color)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$team1Phases",
                        color = team1Color,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = team1Name,
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$balancedPhases",
                        color = TextTertiary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Balanced",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$team2Phases",
                        color = team2Color,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = team2Name,
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }
            
            if (leader.first != "Balanced") {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = CardBorder.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Whatshot,
                        contentDescription = null,
                        tint = leader.second,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${leader.first} dominated the match!",
                        color = leader.second,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

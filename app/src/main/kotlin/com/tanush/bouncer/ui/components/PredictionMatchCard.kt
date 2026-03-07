package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import coil.request.ImageRequest
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.*

@Composable
fun PredictionMatchCard(
    match: CricketMatch,
    team1WinProbability: Float = 0.5f,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    val colors = BouncerDesignSystem.colors
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    
    val statusInfo = getMatchStatusInfo(match.status)
    val isLive = statusInfo.text == "Live"
    
    val animatedProbability by animateFloatAsState(
        targetValue = team1WinProbability,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "probability"
    )
    
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = if (isLive) colors.statusInfo.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.surface1,
                        CardAlmostBlack
                    )
                )
            )
            .clickable {
                haptic.click()
                onClick()
            }
            .padding(22.dp)
    ) {
        // Header with prediction icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = match.matchTitle.substringBefore(","),
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                modifier = Modifier.weight(1f)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = "Prediction",
                    tint = colors.goldPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "AI PREDICTION",
                    color = colors.goldPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Teams with win probability
        PredictionTeamsSection(
            team1 = team1,
            team2 = team2,
            team1Probability = animatedProbability
        )
        
        Spacer(modifier = Modifier.height(18.dp))
        
        // Win probability visualization
        WinProbabilityBar(
            team1Name = team1?.acronym?.takeIf { it.isNotEmpty() }
                ?: team1?.name?.take(3)?.uppercase() ?: "T1",
            team2Name = team2?.acronym?.takeIf { it.isNotEmpty() }
                ?: team2?.name?.take(3)?.uppercase() ?: "T2",
            team1Probability = animatedProbability
        )
        
        Spacer(modifier = Modifier.height(18.dp))
        
        // Key stats comparison
        if (isLive) {
            KeyStatsComparison(match)
        }
    }
}

@Composable
private fun PredictionTeamsSection(
    team1: CricketMatch.Team?,
    team2: CricketMatch.Team?,
    team1Probability: Float
) {
    val context = LocalContext.current
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Team 1
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val logoUrl1 = TeamLogoManager.getBestLogoUrl(team1?.name, team1?.logoUrl, 256, 256)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(8.dp, CircleShape, spotColor = colors.goldPrimary.copy(alpha = 0.3f))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E293B),
                                Color(0xFF0F172A)
                            )
                        ),
                        CircleShape
                    )
                    .padding(3.dp)
            ) {
                CoilImage(
                    model = ImageRequest.Builder(context)
                        .data(logoUrl1)
                        .crossfade(true)
                        .size(256)
                        .build(),
                    contentDescription = team1?.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = team1?.acronym?.takeIf { it.isNotEmpty() }
                    ?: team1?.name?.take(3)?.uppercase() ?: "T1",
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Win probability percentage
            Box(
                modifier = Modifier
                    .shadow(6.dp, RoundedCornerShape(12.dp), spotColor = colors.goldPrimary.copy(alpha = 0.3f))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                colors.goldPrimary.copy(alpha = 0.2f),
                                colors.goldPrimary.copy(alpha = 0.15f)
                            )
                        ),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "${(team1Probability * 100).toInt()}%",
                    color = colors.goldPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
        
        // VS separator
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .size(48.dp)
                .background(colors.glassSurface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "VS",
                color = colors.textMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
        
        // Team 2
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val logoUrl2 = TeamLogoManager.getBestLogoUrl(team2?.name, team2?.logoUrl, 256, 256)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(8.dp, CircleShape, spotColor = colors.goldPrimary.copy(alpha = 0.3f))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E293B),
                                Color(0xFF0F172A)
                            )
                        ),
                        CircleShape
                    )
                    .padding(3.dp)
            ) {
                CoilImage(
                    model = ImageRequest.Builder(context)
                        .data(logoUrl2)
                        .crossfade(true)
                        .size(256)
                        .build(),
                    contentDescription = team2?.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = team2?.acronym?.takeIf { it.isNotEmpty() }
                    ?: team2?.name?.take(3)?.uppercase() ?: "T2",
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .shadow(6.dp, RoundedCornerShape(12.dp), spotColor = colors.goldPrimary.copy(alpha = 0.3f))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                colors.goldPrimary.copy(alpha = 0.2f),
                                colors.goldPrimary.copy(alpha = 0.15f)
                            )
                        ),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "${((1 - team1Probability) * 100).toInt()}%",
                    color = colors.goldPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun WinProbabilityBar(
    team1Name: String,
    team2Name: String,
    team1Probability: Float
) {
    val colors = BouncerDesignSystem.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = team1Name,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = team2Name,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(GlassBorder)
        ) {
            // Team 1 probability fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(team1Probability)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                colors.goldLight,
                                colors.goldPrimary,
                                colors.goldPrimary
                            )
                        )
                    )
            )
            
            // Center marker
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(TextMuted)
            )
        }
    }
}

@Composable
private fun KeyStatsComparison(match: CricketMatch) {
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    val colors = BouncerDesignSystem.colors
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                GlassSurface,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "LIVE STATS",
            color = colors.goldPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Run rate comparison (simulated)
        StatComparisonRow(
            label = "Run Rate",
            team1Value = 7.5f,
            team2Value = 6.8f,
            maxValue = 12f,
            format = { "%.1f" }
        )
        
        // Wickets comparison
        StatComparisonRow(
            label = "Wickets",
            team1Value = 3f,
            team2Value = 4f,
            maxValue = 10f,
            format = { "%.0f" },
            inverse = true
        )
    }
}

@Composable
private fun StatComparisonRow(
    label: String,
    team1Value: Float,
    team2Value: Float,
    maxValue: Float,
    format: (Float) -> String,
    inverse: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(format(team1Value), team1Value),
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = colors.textTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = String.format(format(team2Value), team2Value),
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Comparison bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(GlassBorder)
        ) {
            val team1Fraction = (team1Value / maxValue).coerceIn(0f, 1f)
            val team2Fraction = (team2Value / maxValue).coerceIn(0f, 1f)
            
            Row(modifier = Modifier.fillMaxSize()) {
                // Team 1 bar
                Box(
                    modifier = Modifier
                        .weight(if (inverse) 1f - team1Fraction else team1Fraction)
                        .fillMaxHeight()
                        .background(
                            if (!inverse && team1Value > team2Value) colors.goldPrimary 
                            else GlassSurface
                        )
                )
                
                Spacer(modifier = Modifier.weight(2f - team1Fraction - team2Fraction))
                
                // Team 2 bar
                Box(
                    modifier = Modifier
                        .weight(if (inverse) 1f - team2Fraction else team2Fraction)
                        .fillMaxHeight()
                        .background(
                            if (!inverse && team2Value > team1Value) colors.goldPrimary
                            else GlassSurface
                        )
                )
            }
        }
    }
}

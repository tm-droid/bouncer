package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * MEGA UI UPGRADE: PartnershipsCard (v2.0).
 * High-fidelity partnership visualization with broadcast-grade technical aesthetics.
 */
@Composable
fun PartnershipsCard(
    partnerships: List<Partnership>,
    teamName: String,
    teamColor: Color = ShinyGoldDeep,
    modifier: Modifier = Modifier
) {
    val maxRuns = partnerships.maxOfOrNull { it.totalRuns } ?: 100
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.08f).copy(alpha = 0.8f))
            .border(
                0.5.dp,
                Brush.linearGradient(listOf(GoldHairline, Color.White.copy(alpha = 0.05f))),
                RoundedCornerShape(28.dp)
            )
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // Header: Technical ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "PARTNERSHIP DYNAMICS",
                        color = ShinyGoldDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = teamName.uppercase(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = ShinyGoldDeep.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Partnership Matrix
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                partnerships.forEachIndexed { index, partnership ->
                    PartnershipTechnicalRow(
                        partnership = partnership,
                        maxRuns = maxRuns,
                        teamColor = teamColor
                    )
                }
            }

            // Analytic Summary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PartnershipSummaryStat("TOTAL STAND", partnerships.sumOf { it.totalRuns }.toString(), teamColor)
                    VerticalDivider(modifier = Modifier.height(24.dp).alpha(0.1f), color = Color.White)
                    PartnershipSummaryStat("BEST STAND", (partnerships.maxOfOrNull { it.totalRuns } ?: 0).toString(), SuccessGreen)
                    VerticalDivider(modifier = Modifier.height(24.dp).alpha(0.1f), color = Color.White)
                    PartnershipSummaryStat("50+ POSTS", partnerships.count { it.totalRuns >= 50 }.toString(), ShinyGoldDeep)
                }
            }
        }
    }
}

@Composable
private fun PartnershipTechnicalRow(
    partnership: Partnership,
    maxRuns: Int,
    teamColor: Color
) {
    val progress = partnership.totalRuns.toFloat() / maxRuns
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "progress"
    )
    
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Identification Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (partnership.isCurrentPartnership) teamColor.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f))
                        .border(0.5.dp, if (partnership.isCurrentPartnership) teamColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = partnership.wicketNumber.toString(),
                        color = if (partnership.isCurrentPartnership) teamColor else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = "WICKET PARTNERSHIP",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = partnership.totalRuns.toString(),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "(${partnership.totalBalls})",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Distribution Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.03f))
        ) {
            val p1Ratio = if (partnership.totalRuns > 0) partnership.batsman1Runs.toFloat() / partnership.totalRuns else 0.5f

            Row(modifier = Modifier.fillMaxSize().fillMaxWidth(animatedProgress)) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(p1Ratio.coerceAtLeast(0.01f))
                        .background(teamColor)
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight((1f - p1Ratio).coerceAtLeast(0.01f))
                        .background(teamColor.copy(alpha = 0.3f))
                )
            }
        }

        // Player Telemetry
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TechnicalPartnerInfo(partnership.batsman1Name, partnership.batsman1Runs, partnership.batsman1Balls, teamColor)
            TechnicalPartnerInfo(partnership.batsman2Name, partnership.batsman2Runs, partnership.batsman2Balls, teamColor.copy(alpha = 0.6f), isRightAligned = true)
        }
    }
}

@Composable
private fun TechnicalPartnerInfo(name: String, runs: Int, balls: Int, color: Color, isRightAligned: Boolean = false) {
    Column(horizontalAlignment = if (isRightAligned) Alignment.End else Alignment.Start) {
        Text(
            text = name.uppercase(),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!isRightAligned) Box(modifier = Modifier.size(4.dp).background(color, CircleShape))
            Text(
                text = "$runs ($balls)",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            if (isRightAligned) Box(modifier = Modifier.size(4.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun PartnershipSummaryStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

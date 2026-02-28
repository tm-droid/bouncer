package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Partnership analysis card showing batting partnerships
 */

data class PartnershipInfo(
    val batsman1Name: String,
    val batsman2Name: String,
    val batsman1Runs: Int,
    val batsman2Runs: Int,
    val batsman1Balls: Int,
    val batsman2Balls: Int,
    val totalRuns: Int,
    val totalBalls: Int,
    val partnershipNumber: Int,
    val fours: Int = 0,
    val sixes: Int = 0,
    val runRate: Float = 0f,
    val extras: Int = 0,
    val isActive: Boolean = true,
    val batsman1ImageUrl: String? = null,
    val batsman2ImageUrl: String? = null
)

@Composable
fun PartnershipAnalysisCard(
    partnership: PartnershipInfo,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "partnership_animation")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    val linkRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "link_rotation"
    )
    
    val batsman1Contribution by animateFloatAsState(
        targetValue = if (partnership.totalRuns > 0) 
            partnership.batsman1Runs.toFloat() / partnership.totalRuns else 0.5f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "batsman1_contribution"
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D1117)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = ShinyGoldMid,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Partnership",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = ShinyGoldDeep.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "#${partnership.partnershipNumber}",
                            color = ShinyGoldMid,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    if (partnership.isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(LiveBlue.copy(alpha = pulseAlpha))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Partnership total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Partnership visualization with connecting arc
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = minOf(size.width * 0.3f, size.height * 0.8f)
                        
                        // Connecting arcs
                        rotate(degrees = linkRotation, pivot = center) {
                            for (i in 0 until 4) {
                                val startAngle = i * 90f
                                drawArc(
                                    color = ShinyGoldMid.copy(alpha = 0.3f),
                                    startAngle = startAngle,
                                    sweepAngle = 45f,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
                                )
                            }
                        }
                    }
                    
                    // Center content
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${partnership.totalRuns}",
                            color = ShinyGoldMid,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "${partnership.totalBalls} balls",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Contribution split bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = partnership.batsman1Name.split(" ").lastOrNull() ?: partnership.batsman1Name,
                        color = ChartBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = partnership.batsman2Name.split(" ").lastOrNull() ?: partnership.batsman2Name,
                        color = ShinyGoldMid,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(batsman1Contribution.coerceAtLeast(0.01f))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(ChartBlue.copy(alpha = 0.7f), ChartBlue)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (batsman1Contribution > 0.15f) {
                            Text(
                                text = "${(batsman1Contribution * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (partnership.extras > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight((partnership.extras.toFloat() / partnership.totalRuns).coerceAtLeast(0.01f))
                                .background(Color.Gray)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight((1f - batsman1Contribution - (partnership.extras.toFloat() / partnership.totalRuns.coerceAtLeast(1))).coerceAtLeast(0.01f))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(ShinyGoldMid, ShinyGoldMid.copy(alpha = 0.7f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if ((1f - batsman1Contribution) > 0.15f) {
                            Text(
                                text = "${((1f - batsman1Contribution) * 100).toInt()}%",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Individual batsman stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BatsmanContributionBox(
                    name = partnership.batsman1Name,
                    runs = partnership.batsman1Runs,
                    balls = partnership.batsman1Balls,
                    color = ChartBlue,
                    imageUrl = partnership.batsman1ImageUrl,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                BatsmanContributionBox(
                    name = partnership.batsman2Name,
                    runs = partnership.batsman2Runs,
                    balls = partnership.batsman2Balls,
                    color = ShinyGoldMid,
                    imageUrl = partnership.batsman2ImageUrl,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Partnership stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PartnershipStatBox(
                    label = "RUN RATE",
                    value = String.format("%.2f", partnership.runRate),
                    color = SuccessGreen
                )
                PartnershipStatBox(
                    label = "FOURS",
                    value = "${partnership.fours}",
                    color = BoundaryFour
                )
                PartnershipStatBox(
                    label = "SIXES",
                    value = "${partnership.sixes}",
                    color = BoundarySix
                )
                PartnershipStatBox(
                    label = "EXTRAS",
                    value = "${partnership.extras}",
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun BatsmanContributionBox(
    name: String,
    runs: Int,
    balls: Int,
    color: Color,
    imageUrl: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar placeholder or image
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    CoilImage(
                        model = imageUrl,
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = name.take(1).uppercase(),
                        color = color,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = name.split(" ").lastOrNull() ?: name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$runs",
                    color = color,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " ($balls)",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            
            // Strike rate
            val sr = if (balls > 0) (runs * 100f / balls) else 0f
            Text(
                text = "SR: ${String.format("%.1f", sr)}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun PartnershipStatBox(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 9.sp,
            letterSpacing = 0.5.sp
        )
    }
}

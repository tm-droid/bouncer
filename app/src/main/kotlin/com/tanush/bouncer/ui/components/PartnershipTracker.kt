package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Data class for partnership information
 */
data class Partnership(
    val wicketNumber: Int,
    val batsman1Name: String,
    val batsman1Runs: Int,
    val batsman1Balls: Int,
    val batsman2Name: String,
    val batsman2Runs: Int,
    val batsman2Balls: Int,
    val totalRuns: Int,
    val totalBalls: Int,
    val isCurrentPartnership: Boolean = false
)

/**
 * Premium Partnership Tracker with animated visualizations
 */
@Composable
fun PartnershipTracker(
    partnerships: List<Partnership>,
    modifier: Modifier = Modifier,
    title: String = "Partnerships"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "partnershipAnim")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = ShinyGoldDeep.copy(alpha = 0.12f),
                ambientColor = Color.Black.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
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
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.3).sp
                )
                
                // Total runs from partnerships
                val totalRuns = partnerships.sumOf { it.totalRuns }
                Text(
                    text = "$totalRuns runs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ShinyGoldDeep
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Current partnership highlight
            partnerships.find { it.isCurrentPartnership }?.let { current ->
                CurrentPartnershipCard(
                    partnership = current,
                    pulseAlpha = pulseAlpha,
                    shimmerOffset = shimmerOffset
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Partnership bars visualization
            if (partnerships.isNotEmpty()) {
                PartnershipBarsChart(
                    partnerships = partnerships,
                    shimmerOffset = shimmerOffset
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Partnership list
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                partnerships.filter { !it.isCurrentPartnership }.forEach { partnership ->
                    PartnershipListItem(partnership = partnership)
                }
            }
        }
    }
}

@Composable
private fun CurrentPartnershipCard(
    partnership: Partnership,
    pulseAlpha: Float,
    shimmerOffset: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ShinyGoldDeep.copy(alpha = 0.12f),
                        ShinyGoldMid.copy(alpha = 0.08f),
                        ShinyGoldDeep.copy(alpha = 0.12f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        ShinyGoldDeep.copy(alpha = pulseAlpha * 0.5f),
                        ShinyGoldLight.copy(alpha = 0.3f),
                        ShinyGoldDeep.copy(alpha = pulseAlpha * 0.5f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        SuccessGreen.copy(alpha = pulseAlpha),
                                        SuccessGreen.copy(alpha = 0.3f)
                                    )
                                ),
                                CircleShape
                            )
                    )
                    Text(
                        text = "CURRENT PARTNERSHIP",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = SuccessGreen
                    )
                }
                
                Text(
                    text = "${partnership.wicketNumber}${getOrdinalSuffix(partnership.wicketNumber)} wicket",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Main stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Batsman 1
                PartnershipBatsmanStat(
                    name = partnership.batsman1Name,
                    runs = partnership.batsman1Runs,
                    balls = partnership.batsman1Balls,
                    percentage = if (partnership.totalRuns > 0) 
                        partnership.batsman1Runs * 100 / partnership.totalRuns else 0,
                    color = ChartBlue
                )
                
                // Total in center
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        Text(
                            text = "${partnership.totalRuns}",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = ShinyGoldDeep
                        )
                        
                        // Shimmer overlay
                        Text(
                            text = "${partnership.totalRuns}",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Transparent,
                            modifier = Modifier.drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            ShinyGoldLight.copy(alpha = 0.5f),
                                            Color.Transparent
                                        ),
                                        startX = size.width * shimmerOffset - size.width * 0.5f,
                                        endX = size.width * shimmerOffset + size.width * 0.5f
                                    ),
                                    blendMode = BlendMode.SrcIn
                                )
                            }
                        )
                    }
                    
                    Text(
                        text = "${partnership.totalBalls} balls",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    
                    val runRate = if (partnership.totalBalls > 0) 
                        (partnership.totalRuns * 6.0 / partnership.totalBalls) else 0.0
                    Text(
                        text = "RR ${String.format("%.2f", runRate)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            runRate >= 10 -> SuccessGreen
                            runRate >= 6 -> ShinyGoldDeep
                            else -> TextSecondary
                        }
                    )
                }
                
                // Batsman 2
                PartnershipBatsmanStat(
                    name = partnership.batsman2Name,
                    runs = partnership.batsman2Runs,
                    balls = partnership.batsman2Balls,
                    percentage = if (partnership.totalRuns > 0) 
                        partnership.batsman2Runs * 100 / partnership.totalRuns else 0,
                    color = ChartOrange
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Contribution bar
            ContributionBar(
                value1 = partnership.batsman1Runs.toFloat(),
                value2 = partnership.batsman2Runs.toFloat(),
                color1 = ChartBlue,
                color2 = ChartOrange
            )
        }
    }
}

@Composable
private fun PartnershipBatsmanStat(
    name: String,
    runs: Int,
    balls: Int,
    percentage: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        Text(
            text = name.split(" ").lastOrNull() ?: name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "$runs",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        
        Text(
            text = "$balls balls",
            fontSize = 10.sp,
            color = TextMuted
        )
        
        Text(
            text = "$percentage%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ContributionBar(
    value1: Float,
    value2: Float,
    color1: Color,
    color2: Color
) {
    val total = value1 + value2
    val percentage1 = if (total > 0) value1 / total else 0.5f
    
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage1,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "barProgress"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(animatedPercentage.coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                color1.copy(alpha = 0.7f),
                                color1
                            )
                        )
                    )
            )
            
            Box(
                modifier = Modifier
                    .weight((1 - animatedPercentage).coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                color2,
                                color2.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun PartnershipBarsChart(
    partnerships: List<Partnership>,
    shimmerOffset: Float
) {
    val maxRuns = partnerships.maxOfOrNull { it.totalRuns } ?: 1
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        partnerships.sortedBy { it.wicketNumber }.forEach { partnership ->
            val percentage = partnership.totalRuns.toFloat() / maxRuns
            
            val animatedPercentage by animateFloatAsState(
                targetValue = percentage,
                animationSpec = tween(1000, easing = FastOutSlowInEasing),
                label = "barAnim${partnership.wicketNumber}"
            )
            
            val barColor = if (partnership.isCurrentPartnership) SuccessGreen else ShinyGoldDeep
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Wicket number
                Text(
                    text = "${partnership.wicketNumber}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted,
                    modifier = Modifier.width(16.dp)
                )
                
                // Bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CardDarkBlue)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedPercentage)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        barColor.copy(alpha = 0.4f),
                                        barColor.copy(alpha = 0.7f),
                                        barColor
                                    )
                                )
                            )
                    )
                    
                    // Shimmer for current partnership
                    if (partnership.isCurrentPartnership) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedPercentage)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.2f),
                                            Color.Transparent
                                        ),
                                        startX = (shimmerOffset - 0.3f) * 1000f,
                                        endX = shimmerOffset * 1000f
                                    )
                                )
                        )
                    }
                }
                
                // Runs
                Text(
                    text = "${partnership.totalRuns}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (partnership.isCurrentPartnership) SuccessGreen else TextPrimary,
                    modifier = Modifier.width(32.dp)
                )
            }
        }
    }
}

@Composable
private fun PartnershipListItem(
    partnership: Partnership
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.White.copy(alpha = 0.08f).copy(alpha = 0.3f),
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Wicket and batsmen
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        CardDarkBlue,
                        RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${partnership.wicketNumber}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
            }
            
            Column {
                Text(
                    text = "${partnership.batsman1Name.split(" ").lastOrNull() ?: partnership.batsman1Name} & ${partnership.batsman2Name.split(" ").lastOrNull() ?: partnership.batsman2Name}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "${partnership.batsman1Runs}(${partnership.batsman1Balls}) - ${partnership.batsman2Runs}(${partnership.batsman2Balls})",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
        
        // Total
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${partnership.totalRuns}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (partnership.totalRuns >= 50) ShinyGoldDeep else TextPrimary
            )
            Text(
                text = "${partnership.totalBalls} balls",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}

private fun getOrdinalSuffix(number: Int): String {
    return when {
        number % 100 in 11..13 -> "th"
        number % 10 == 1 -> "st"
        number % 10 == 2 -> "nd"
        number % 10 == 3 -> "rd"
        else -> "th"
    }
}

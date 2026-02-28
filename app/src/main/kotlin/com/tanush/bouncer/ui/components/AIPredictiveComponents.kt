package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * AI-Powered Predictive Forecast Components.
 * Inspired by the MCP Predictive Forecast mockup and Cricbuzz AI features.
 */

data class AIPrediction(
    val ballNumber: String,
    val predictedOutcome: String,
    val confidence: Float, // 0.0 to 1.0
    val isHighlight: Boolean = false
)

/**
 * Next Over Forecast Carousel.
 * Ball-by-ball prediction cards with confidence rings.
 */
@Composable
fun NextOverForecastCarousel(
    overNumber: Int,
    predictions: List<AIPrediction>,
    engineConfidence: Float = 0.84f,
    onViewHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ShinyGoldDeep,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "NEXT OVER FORECAST",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Over ${overNumber + 1} Predictions",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            TextButton(onClick = onViewHistory) {
                Text(
                    text = "View History",
                    color = ShinyGoldDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = ShinyGoldDeep,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Prediction Cards Carousel
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(predictions) { prediction ->
                AIPredictionCard(
                    prediction = prediction,
                    overNumber = overNumber
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Engine Confidence Footer
        EngineConfidenceBadge(
            confidence = engineConfidence,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun AIPredictionCard(
    prediction: AIPrediction,
    overNumber: Int
) {
    val isHighConfidence = prediction.confidence >= 0.8f
    val isCritical = prediction.predictedOutcome.equals("Wicket", ignoreCase = true)
    
    val accentColor = when {
        isCritical -> WicketRed
        prediction.predictedOutcome.contains("6") -> BoundarySix
        prediction.predictedOutcome.contains("4") || prediction.predictedOutcome.contains("Boundary") -> BoundaryFour
        else -> ShinyGoldDeep
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "predictionPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Surface(
        modifier = Modifier
            .width(140.dp)
            .height(180.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isCritical) 
            Color.White.copy(alpha = 0.08f) 
        else if (prediction.isHighlight) 
            accentColor.copy(alpha = 0.05f) 
        else 
            Color.White.copy(alpha = 0.05f),
        border = BorderStroke(
            if (isCritical) 1.5.dp else 0.5.dp,
            if (isCritical) accentColor.copy(alpha = pulseAlpha) 
            else Gold400.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ball Number
            Text(
                text = prediction.ballNumber,
                color = if (isCritical) accentColor else Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            
            if (isCritical) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Confidence Ring
            Box(
                modifier = Modifier.size(70.dp),
                contentAlignment = Alignment.Center
            ) {
                ConfidenceRing(
                    confidence = prediction.confidence,
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxSize()
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(prediction.confidence * 100).toInt()}",
                        color = if (isCritical) accentColor else Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "%",
                        color = if (isCritical) accentColor.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Outcome Badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isCritical) accentColor else Color.White.copy(alpha = 0.05f)
            ) {
                Text(
                    text = prediction.predictedOutcome.uppercase(),
                    color = if (isCritical) BlackBackground else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Confidence Label
            Text(
                text = when {
                    isCritical -> "Critical Alert"
                    isHighConfidence -> "High Confidence"
                    prediction.confidence >= 0.5f -> "Moderate"
                    else -> "Uncertain"
                },
                color = if (isCritical) accentColor else Color.White.copy(alpha = 0.4f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ConfidenceRing(
    confidence: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val sweepAngle = confidence * 360f
    
    val animatedSweep by animateFloatAsState(
        targetValue = sweepAngle,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "sweep"
    )
    
    Canvas(modifier = modifier) {
        val strokeWidth = 6.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)
        
        // Background Ring
        drawCircle(
            color = Color.White.copy(alpha = 0.1f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        
        // Progress Ring
        drawArc(
            color = accentColor,
            startAngle = -90f,
            sweepAngle = animatedSweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun EngineConfidenceBadge(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Engine Confidence Card
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(0.5.dp, Gold400.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = ShinyGoldDeep,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "ENGINE",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "CONFIDENCE",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${(confidence * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "+2.1%",
                        color = SuccessGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
        
        // MCP Live Badge
        Surface(
            modifier = Modifier.weight(0.6f),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(0.5.dp, ShinyGoldDeep.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ShinyGoldDeep)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "MCP",
                        color = ShinyGoldDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "LIVE",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * AI Analysis Card - Match Insights.
 * Premium analysis cards similar to Cricbuzz AI predictions.
 */
@Composable
fun AiAnalysisCard(
    title: String,
    description: String,
    highlightText: String = "",
    type: AnalysisType = AnalysisType.NEUTRAL,
    modifier: Modifier = Modifier
) {
    val accentColor = when (type) {
        AnalysisType.POSITIVE -> SuccessGreen
        AnalysisType.WARNING -> WarningOrange
        AnalysisType.CRITICAL -> WicketRed
        AnalysisType.NEUTRAL -> ShinyGoldDeep
    }
    
    val icon = when (type) {
        AnalysisType.POSITIVE -> Icons.Default.TrendingUp
        AnalysisType.WARNING -> Icons.Default.Warning
        AnalysisType.CRITICAL -> Icons.Default.ErrorOutline
        AnalysisType.NEUTRAL -> Icons.Default.Landscape
    }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Accent Line
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(100.dp)
                    .background(accentColor)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.1f))
                        .border(0.5.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = description,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                    
                    if (highlightText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = highlightText,
                            color = accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

enum class AnalysisType {
    POSITIVE,
    WARNING,
    CRITICAL,
    NEUTRAL
}

/**
 * Pitch Condition Widget.
 * Quick pitch condition and weather info.
 */
@Composable
fun PitchConditionWidget(
    temperature: String,
    condition: String,
    humidity: String,
    windSpeed: String,
    pitchType: String = "Batting Friendly",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(0.5.dp, Gold400.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Weather Sphere
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = ShinyGoldDeep,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Temperature & Condition
            Column {
                Text(
                    text = temperature,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = condition.uppercase(),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            // Stats
            Column(horizontalAlignment = Alignment.End) {
                WeatherMiniStat(label = "Humidity", value = humidity)
                Spacer(modifier = Modifier.height(8.dp))
                WeatherMiniStat(label = "Wind", value = windSpeed)
            }
        }
    }
}

@Composable
private fun WeatherMiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label.uppercase(),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

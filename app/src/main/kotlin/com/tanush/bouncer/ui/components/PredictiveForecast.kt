package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

data class BallPrediction(
    val ballNumber: String,
    val probability: Int,
    val prediction: String,
    val isCritical: Boolean = false
)

@Composable
fun PredictiveForecastSection(
    predictions: List<BallPrediction>,
    overNumber: Int,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Next Over (${overNumber}th) Forecast",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "View History",
                color = hierarchy.secondary.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(predictions) { prediction ->
                PredictiveBallCard(prediction)
            }
        }
    }
}

@Composable
fun PredictiveBallCard(prediction: BallPrediction) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val goldPrimary = hierarchy.secondary.primary
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val cardBackground = if (prediction.isCritical) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1A1A1A), Color(0xFFF9D406).copy(alpha = 0.1f))
        )
    } else {
        Brush.verticalGradient(colors = listOf(Color(0xFF1A1A1A), Color(0xFF1A1A1A)))
    }

    val borderColor = if (prediction.isCritical) {
        goldPrimary.copy(alpha = 0.4f)
    } else {
        GlassBorder
    }

    Surface(
        modifier = Modifier
            .width(160.dp)
            .height(180.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBackground)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = prediction.ballNumber,
                color = if (prediction.isCritical) goldPrimary else TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart)
            )

            if (prediction.isCritical) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(goldPrimary)
                        .align(Alignment.TopEnd)
                        .drawBehind {
                            drawCircle(
                                color = goldPrimary.copy(alpha = pulseAlpha),
                                radius = size.minDimension * 2f
                            )
                        }
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PredictionProgressRing(
                    probability = prediction.probability,
                    isCritical = prediction.isCritical,
                    pulseAlpha = pulseAlpha
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (prediction.isCritical) goldPrimary else GlassSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = prediction.prediction.uppercase(),
                        color = if (prediction.isCritical) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = if (prediction.isCritical) "CRITICAL ALERT" else "STRONG SIGNAL",
                    color = if (prediction.isCritical) goldPrimary else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PredictionProgressRing(
    probability: Int,
    isCritical: Boolean,
    pulseAlpha: Float
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val goldPrimary = BouncerDesignSystem.hierarchy.secondary.primary
    val animatedProgress by animateFloatAsState(
        targetValue = probability / 100f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(80.dp)) {
            val strokeWidthPx = 6.dp.toPx()
            
            // Background ring
            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                style = Stroke(width = strokeWidthPx)
            )
            
            // Progress arc
            drawArc(
                color = goldPrimary,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
            
            if (isCritical) {
                drawCircle(
                    color = goldPrimary.copy(alpha = pulseAlpha * 0.2f),
                    radius = (size.minDimension / 2) + 8.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$probability%",
                color = if (isCritical) goldPrimary else Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.models.BallPrediction

/**
 * PredictiveBallCard - AI Telemetry variant.
 * Unified with models.BallPrediction.
 */
@Composable
fun PredictiveBallCard(prediction: BallPrediction) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulse"
    )

    val isCritical = prediction.probability > 0.8f
    
    // Capture colors for Canvas scope
    val goldPrimaryColor = colors.goldPrimary
    val goldDarkColor = colors.goldDark

    Box(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isCritical) {
                    Brush.verticalGradient(listOf(GlassSurface, colors.goldPrimary.copy(alpha = 0.15f)))
                } else {
                    Brush.verticalGradient(listOf(GlassSurface, Color.Transparent))
                }
            )
            .border(
                1.dp, 
                if (isCritical) colors.goldPrimary.copy(alpha = 0.4f) else GlassBorderLight, 
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "BALL ${prediction.ballIndex}", 
                    color = if (isCritical) colors.goldPrimary else colors.textSecondary, 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Bold
                )
                if (isCritical) {
                    Box(modifier = Modifier.size(6.dp).background(colors.goldPrimary, CircleShape))
                }
            }

            // Conic Progress Ring
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                if (isCritical) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                            .border(1.dp, colors.goldPrimary.copy(alpha = 0.2f), CircleShape)
                    )
                }

                androidx.compose.foundation.Canvas(modifier = Modifier.size(80.dp)) {
                    drawCircle(
                        color = GlassSurface,
                        style = Stroke(width = 6.dp.toPx())
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(goldPrimaryColor, goldDarkColor, goldPrimaryColor)),
                        startAngle = -90f,
                        sweepAngle = prediction.probability * 360f,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(prediction.probability * 100).toInt()}%", 
                        color = if (isCritical) colors.goldPrimary else Color.White, 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isCritical) colors.goldPrimary else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    prediction.type.uppercase(),
                    color = if (isCritical) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

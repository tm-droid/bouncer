package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*

/**
 * PressureIndexHeatmapCard - ELITE MASTERPIECE EDITION (v1.0).
 * High-fidelity AI visualization of match pressure zones.
 * Inspired by Quantum Bouncer mockups.
 */
@Composable
fun PressureIndexHeatmapCard(modifier: Modifier = Modifier) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "heatmap")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(GlassSurface)
            .border(0.5.dp, GoldHairline.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
    ) {
        // Background: Abstract Stadium Texture
        CoilImage(
            model = "https://images.unsplash.com/photo-1531415074968-036ba1b575da?q=80&w=800",
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.25f),
            contentScale = ContentScale.Crop
        )

        // Heatmap Visualization Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Dynamic Pressure Blobs
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Red.copy(alpha = 0.4f * pulseAlpha), Color.Transparent)
                ),
                radius = 120.dp.toPx(),
                center = Offset(size.width * 0.35f, size.height * 0.65f)
            )
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(hierarchy.secondary.primary.copy(alpha = 0.3f * pulseAlpha), Color.Transparent)
                ),
                radius = 160.dp.toPx(),
                center = Offset(size.width * 0.7f, size.height * 0.3f)
            )

            // Technical Grid Lines
            val gridStroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            drawRect(
                color = Color.White.copy(alpha = 0.05f),
                style = gridStroke
            )
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.Bottom
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(modifier = Modifier.size(8.dp).background(hierarchy.secondary.primary, RoundedCornerShape(2.dp)))
                Text(
                    "PRESSURE INDICES",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            Text(
                "AI-POWERED LENGTH ANALYSIS",
                color = hierarchy.secondary.primary.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        // Live Telemetry Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(GlassSurface)
                .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                "LIVE SYNC",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

package com.tanush.bouncer.ui.components.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.GoldSubtle
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * 3D-perspective view of a cricket pitch using graphicsLayer transformations.
 * Used for pitch maps, ball tracking visualizations, and heatmaps.
 */
@Composable
fun Pitch3DView(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        // 3D Transformed Pitch Surface (Refined to 45f rotation & 800px perspective)
        Box(
            modifier = Modifier
                .width(220.dp) // From _16/code.html
                .height(340.dp) // From _16/code.html
                .graphicsLayer {
                    rotationX = 45f
                    cameraDistance = 800f // Explicitly 800px perspective
                }
                .clip(RoundedCornerShape(100.dp)) // Oval surface spec
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GoldSubtle.copy(alpha = 0.4f),
                            Color(0xFF2A3A20).copy(alpha = 0.6f),
                            GoldSubtle.copy(alpha = 0.4f)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    color = colors.glassBorder,
                    shape = RoundedCornerShape(100.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pitchWidth = size.width
                val pitchHeight = size.height
                
                // Field Circle (Inner 30-yard circle)
                drawOval(
                    color = Color.White.copy(alpha = 0.1f),
                    topLeft = Offset(pitchWidth * 0.15f, pitchHeight * 0.1f),
                    size = Size(pitchWidth * 0.7f, pitchHeight * 0.8f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                )

                // Pitch Strip (The central rectangle)
                drawRect(
                    color = GoldPrimary.copy(alpha = 0.12f),
                    topLeft = Offset(pitchWidth * 0.4f, pitchHeight * 0.2f),
                    size = Size(pitchWidth * 0.2f, pitchHeight * 0.6f)
                )

                // Crease Lines
                val lineOpacity = 0.4f
                val lineWidth = 1.dp.toPx()
                
                // Bowling Creases (near the ends of the strip)
                drawLine(
                    color = Color.White.copy(alpha = lineOpacity),
                    start = Offset(pitchWidth * 0.4f, pitchHeight * 0.25f),
                    end = Offset(pitchWidth * 0.6f, pitchHeight * 0.25f),
                    strokeWidth = lineWidth
                )
                
                drawLine(
                    color = Color.White.copy(alpha = lineOpacity),
                    start = Offset(pitchWidth * 0.4f, pitchHeight * 0.75f),
                    end = Offset(pitchWidth * 0.6f, pitchHeight * 0.75f),
                    strokeWidth = lineWidth
                )
                
                // Stumps (Simplified 3D-feel representation)
                val stumpWidth = 6.dp.toPx()
                val stumpHeight = 2.dp.toPx()
                
                // Far Stumps
                drawRoundRect(
                    color = GoldPrimary,
                    topLeft = Offset(pitchWidth * 0.5f - stumpWidth/2, pitchHeight * 0.25f - stumpHeight/2),
                    size = Size(stumpWidth, stumpHeight),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                
                // Near Stumps
                drawRoundRect(
                    color = GoldPrimary,
                    topLeft = Offset(pitchWidth * 0.5f - stumpWidth/2, pitchHeight * 0.75f - stumpHeight/2),
                    size = Size(stumpWidth, stumpHeight),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
        
        // Content overlay (balls, heatmaps etc) - Match transformations
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(340.dp)
                .graphicsLayer {
                    rotationX = 45f
                    cameraDistance = 800f
                },
            content = content
        )
    }
}

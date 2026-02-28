package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.ProbabilityEvent
import com.tanush.bouncer.ui.theme.*

@Composable
fun ProbabilityGraphView(
    events: List<ProbabilityEvent>,
    firstInningsEndBall: Int = 120,
    currentMatchScore: String = "",
    latestEventDescription: String = "",
    matchResult: String = "",
    modifier: Modifier = Modifier,
    predictiveOverlay: List<Pair<Int, Double>>? = null // List of (ball, probability) for projection
) {
    var selectedEvent by remember { mutableStateOf<ProbabilityEvent?>(null) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "graphShimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "graphShimmer"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = ShinyGoldDeep.copy(alpha = 0.12f),
                ambientColor = Color.Black.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Title with gold accent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Win Probability",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.3).sp
                )
                
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            ShinyGoldDeep.copy(alpha = shimmerAlpha),
                            CircleShape
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Match Score
            if (currentMatchScore.isNotEmpty()) {
                Text(
                    text = currentMatchScore,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ShinyGoldDeep,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            
            // Latest Event
            if (latestEventDescription.isNotEmpty()) {
                Text(
                    text = latestEventDescription,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp),
                    lineHeight = 18.sp
                )
            }
            
            // Graph
            if (events.isNotEmpty()) {
                ProbabilityGraph(
                    events = events,
                    firstInningsEndBall = firstInningsEndBall,
                    onEventSelected = { selectedEvent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .padding(vertical = 16.dp),
                    predictiveOverlay = predictiveOverlay
                )
            }
            
            // Match Result
            if (matchResult.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    ShinyGoldDeep.copy(alpha = 0.1f),
                                    ShinyGoldMid.copy(alpha = 0.15f),
                                    ShinyGoldDeep.copy(alpha = 0.1f)
                                )
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp)
                ) {
                    Text(
                        text = matchResult,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ShinyGoldDeep,
                        letterSpacing = 0.2.sp
                    )
                }
            }
            
            // Selected Event Details
            selectedEvent?.let { event ->
                Spacer(modifier = Modifier.height(16.dp))
                EventDetailsCard(
                    event = event,
                    onDismiss = { selectedEvent = null }
                )
            }
            
            // Key Moments
            if (events.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Key Moments",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.2).sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                val keyMoments = events.filter { kotlin.math.abs(it.impact) > 5.0 }
                    .sortedByDescending { kotlin.math.abs(it.impact) }
                    .take(5)
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    items(keyMoments) { event ->
                        KeyMomentItem(event = event)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbabilityGraph(
    events: List<ProbabilityEvent>,
    firstInningsEndBall: Int,
    onEventSelected: (ProbabilityEvent) -> Unit,
    modifier: Modifier = Modifier,
    predictiveOverlay: List<Pair<Int, Double>>? = null
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = ShinyGoldDeep
    val secondaryColor = ShinyGoldMid

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 50f
        
        val maxBall = events.maxOfOrNull { it.ball } ?: 240
        val xScale = (width - 2 * padding) / maxBall
        val yScale = (height - 2 * padding) / 100f
        
        // Draw axes with better styling
        val axisColor = onSurfaceColor.copy(alpha = 0.4f)
        drawLine(
            color = axisColor,
            start = Offset(padding, height - padding),
            end = Offset(width - padding, height - padding),
            strokeWidth = 2.5f
        )
        drawLine(
            color = axisColor,
            start = Offset(padding, padding),
            end = Offset(padding, height - padding),
            strokeWidth = 2.5f
        )
        
        // Draw 50% line with gold tint
        drawLine(
            color = primaryColor.copy(alpha = 0.2f),
            start = Offset(padding, height / 2),
            end = Offset(width - padding, height / 2),
            strokeWidth = 1.5f
        )
        
        // Draw innings separator with gold
        val inningsSeparatorX = padding + firstInningsEndBall * xScale
        drawLine(
            color = primaryColor.copy(alpha = 0.35f),
            start = Offset(inningsSeparatorX, padding),
            end = Offset(inningsSeparatorX, height - padding),
            strokeWidth = 2.5f
        )
        
        // Draw gradient area under the probability line
        val areaPath = Path()
        events.forEachIndexed { index, event ->
            val x = padding + event.ball * xScale
            val y = height - padding - event.probability.toFloat() * yScale
            
            if (index == 0) {
                areaPath.moveTo(x, height - padding)
                areaPath.lineTo(x, y)
            } else {
                areaPath.lineTo(x, y)
            }
        }
        events.lastOrNull()?.let { lastEvent ->
            val lastX = padding + lastEvent.ball * xScale
            areaPath.lineTo(lastX, height - padding)
        }
        areaPath.close()
        
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.15f),
                    Color.Transparent
                )
            )
        )
        
        // Draw probability line with gradient
        val linePath = Path()
        events.forEachIndexed { index, event ->
            val x = padding + event.ball * xScale
            val y = height - padding - event.probability.toFloat() * yScale
            
            if (index == 0) {
                linePath.moveTo(x, y)
            } else {
                linePath.lineTo(x, y)
            }
        }
        
        drawPath(
            path = linePath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    primaryColor,
                    secondaryColor,
                    primaryColor
                )
            ),
            style = Stroke(width = 3.5f)
        )
        
        // Draw event markers with better colors
        events.filter { kotlin.math.abs(it.impact) > 5.0 }.forEach { event ->
            val x = padding + event.ball * xScale
            val y = height - padding - event.probability.toFloat() * yScale
            
            val markerColor = when (event.eventType) {
                ProbabilityEvent.EventType.WICKET -> WicketBlue
                ProbabilityEvent.EventType.BOUNDARY -> BoundarySix
                else -> ShinyGoldMid
            }
            
            // Outer glow
            drawCircle(
                color = markerColor.copy(alpha = 0.25f),
                radius = 12f,
                center = Offset(x, y)
            )
            
            // Inner circle
            drawCircle(
                color = markerColor,
                radius = 5f,
                center = Offset(x, y)
            )
        }
        // Draw predictive overlay if provided
        predictiveOverlay?.let { overlay ->
            if (overlay.size > 1) {
                val overlayPath = Path()
                overlay.forEachIndexed { index, (ball, probability) ->
                    val x = padding + ball * xScale
                    val y = height - padding - probability.toFloat() * yScale
                    if (index == 0) {
                        overlayPath.moveTo(x, y)
                    } else {
                        overlayPath.lineTo(x, y)
                    }
                }
                drawPath(
                    path = overlayPath,
                    color = Color.Magenta.copy(alpha = 0.6f),
                    style = Stroke(width = 2.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                )
            }
        }
    }
}

@Composable
private fun EventDetailsCard(
    event: ProbabilityEvent,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = ShinyGoldDeep.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardDarkBlue
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Over ${event.over}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ShinyGoldDeep
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("×", fontSize = 20.sp, color = TextSecondary)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = event.description,
                fontSize = 14.sp,
                color = TextPrimary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Mock player stats (for demo)
            Text(
                text = "Batsman: Rohit Sharma  45(32)  4x4  2x6  SR: 140.6",
                fontSize = 13.sp,
                color = BoundarySix,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Bowler: Rashid Khan  3-0-18-2  Econ: 6.0",
                fontSize = 13.sp,
                color = WicketBlue,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Mock AI insight
            Text(
                text = "AI Insight: This wicket shifted the win probability by 12%. Momentum now with the bowling side.",
                fontSize = 12.sp,
                color = ShinyGoldDeep,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Win Probability: ${"%.1f".format(event.probability)}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Text(
                    text = "Impact: ${if (event.impact > 0) "+" else ""}${"%.1f".format(event.impact)}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (event.impact > 0) BoundarySix else WicketBlue
                )
            }
        }
    }
}

@Composable
private fun KeyMomentItem(event: ProbabilityEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(
                Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = when (event.eventType) {
                        ProbabilityEvent.EventType.WICKET -> WicketBlue
                        ProbabilityEvent.EventType.BOUNDARY -> BoundarySix
                        else -> LiveBlue
                    },
                    shape = CircleShape
                )
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Over ${event.over} • ${event.description}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Impact: ${if (event.impact > 0) "+" else ""}${"%.1f".format(event.impact)}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (event.impact > 0) BoundarySix else WicketBlue
            )
        }
        
        Text(
            text = "${"%.1f".format(event.probability)}%",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = ShinyGoldDeep
        )
    }
}

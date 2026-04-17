package com.tanush.bouncer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*

@Composable
fun ArBallTrackingScreen(
    match: CricketMatch? = null,
    onBackClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ar_effects")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(BackgroundBase)) {
        // 1. Simulated Camera Feed
        CoilImage(
            model = "https://images.unsplash.com/photo-1531415074968-036ba1b575da?q=80&w=1200",
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.8f),
            contentScale = ContentScale.Crop
        )
        
        // Vignette Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )

        // 2. AR Trajectory Overlay (Canvas)
        ArTrajectoryOverlay(pulse)

        // 3. Reticle UI
        ArReticleFocus()

        // 4. Content Overlay
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            EliteArHeader(match = match, onBackClick = onBackClick)

            Spacer(modifier = Modifier.weight(1f))

            // Speed Highlight
            ArSpeedHeadline(match = match)

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Widgets & Controls
            ArBottomSection(match = match)
        }
    }
}

@Composable
private fun ArTrajectoryOverlay(pulse: Float) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val goldPrimary = hierarchy.secondary.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        // Path
        val path = Path().apply {
            moveTo(50.dp.toPx(), size.height * 0.8f)
            quadraticTo(centerX * 0.6f, centerY * 0.6f, centerX, centerY + 50.dp.toPx())
        }

        // Glow
        drawPath(
            path = path,
            color = goldPrimary.copy(alpha = 0.2f * pulse),
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        )
        // Core
        drawPath(
            path = path,
            color = goldPrimary,
            style = Stroke(
                width = 3.dp.toPx(), 
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f))
            )
        )

        // Impact Point
        drawCircle(
            color = ChartCyan,
            radius = (6.dp.toPx() * pulse) + 4.dp.toPx(),
            center = Offset(centerX, centerY + 50.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = ChartCyan,
            radius = 4.dp.toPx(),
            center = Offset(centerX, centerY + 50.dp.toPx())
        )
    }
}

@Composable
private fun ArReticleFocus() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val goldPrimary = hierarchy.secondary.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width * 0.8f
                val h = size.height * 0.4f
                val left = (size.width - w) / 2
                val top = (size.height - h) / 2
                
                // Corners
                val cornerLen = 32.dp.toPx()
                val stroke = 2.dp.toPx()
                
                // Top Left
                drawLine(goldPrimary, Offset(left, top), Offset(left + cornerLen, top), stroke)
                drawLine(goldPrimary, Offset(left, top), Offset(left, top + cornerLen), stroke)
                
                // Top Right
                drawLine(goldPrimary, Offset(left + w, top), Offset(left + w - cornerLen, top), stroke)
                drawLine(goldPrimary, Offset(left + w, top), Offset(left + w, top + cornerLen), stroke)
                
                // Bottom Left
                drawLine(goldPrimary, Offset(left, top + h), Offset(left + cornerLen, top + h), stroke)
                drawLine(goldPrimary, Offset(left, top + h), Offset(left, top + h - cornerLen), stroke)
                
                // Bottom Right
                drawLine(goldPrimary, Offset(left + w, top + h), Offset(left + w - cornerLen, top + h), stroke)
                drawLine(goldPrimary, Offset(left + w, top + h), Offset(left + w, top + h - cornerLen), stroke)
            }
    )
}

@Composable
private fun EliteArHeader(match: CricketMatch? = null, onBackClick: () -> Unit) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val team1 = match?.teams?.getOrNull(0)?.acronym ?: match?.teams?.getOrNull(0)?.name?.take(3)?.uppercase() ?: "---"
    val team2 = match?.teams?.getOrNull(1)?.acronym ?: match?.teams?.getOrNull(1)?.name?.take(3)?.uppercase() ?: "---"
    val matchLabel = "$team1 vs $team2"
    val battingTeam = match?.teams?.firstOrNull { it.isBatting }
    val currentOver = battingTeam?.overs ?: "--"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.Black.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(32.dp).background(hierarchy.secondary.primary.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.EmergencyRecording, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(matchLabel, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("LIVE FEED • 4K", color = colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
            
            Surface(color = hierarchy.secondary.primary.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                Text("OVER $currentOver", color = hierarchy.secondary.primary, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun ArSpeedHeadline(match: CricketMatch? = null) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    // No ball speed data available on model — show placeholder derived from bowler context
    val speedText = "---"
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                speedText, 
                color = colors.textMuted, 
                fontSize = 72.sp, 
                fontWeight = FontWeight.Black,
                modifier = Modifier.offset(x = 4.dp, y = 4.dp)
            )
            Text(
                speedText, 
                color = hierarchy.secondary.primary, 
                fontSize = 72.sp, 
                fontWeight = FontWeight.Black,
                modifier = Modifier.shadow(20.dp, spotColor = hierarchy.secondary.primary)
            )
        }
        Text("KPH", color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(12.dp))
        Surface(color = Color.Black.copy(alpha = 0.4f), border = BorderStroke(1.dp, ChartCyan.copy(alpha = 0.2f)), shape = RoundedCornerShape(4.dp)) {
            Text("TRAJECTORY LOCKED", color = ChartCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun ArBottomSection(match: CricketMatch? = null) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val bowler = match?.currentBowler
    val econLabel = bowler?.economy ?: "--"
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Floating Widgets
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArWidget("Pitch Map", if (bowler != null) "Live" else "N/A", Icons.Default.Layers, ChartCyan)
            ArWidget("Economy", econLabel, Icons.AutoMirrored.Filled.RotateRight, hierarchy.secondary.primary)
            ArWidget("Wickets", bowler?.wickets ?: "--", Icons.Default.AirlineStops, ChartCyan)
        }

        // Camera Controls
        Surface(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = colors.textSecondary)
                }
                
                // Record FAB
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(hierarchy.secondary.primary.copy(alpha = 0.2f), CircleShape)
                        .border(2.dp, hierarchy.secondary.primary, CircleShape)
                        .padding(6.dp)
                        .background(hierarchy.secondary.primary, CircleShape)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Videocam, null, tint = Color.Black, modifier = Modifier.size(32.dp))
                }

                IconButton(onClick = {}) {
                    Icon(Icons.Default.QueryStats, null, tint = ChartCyan)
                }
            }
        }
    }
}

@Composable
private fun ArWidget(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Surface(
        modifier = Modifier.width(130.dp).height(100.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, GlassSurface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label.uppercase(), color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            }
            Column {
                Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(if (label.contains("Spin")) "RPM" else "Length", color = TextTertiary, fontSize = 9.sp)
            }
        }
    }
}

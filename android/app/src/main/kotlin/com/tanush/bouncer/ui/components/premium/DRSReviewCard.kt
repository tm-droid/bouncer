package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.DRSReviewData
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * DRS Review Card - "Elite" Dramatic UI (v2.0).
 * Matches Mockup: Neon Cyan, Dramatic Verdict, and Delivery Metrics.
 */

@Composable
fun DRSReviewCard(
    review: DRSReviewData,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    // No ambient loop. Motion in this app reports a state change (a score tick, a
    // toggle); a permanent breathing glow reports nothing and is the kind of
    // decoration this design system spends its budget away from.
    val neonPulse = 0.7f

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Dramatic DRS Visualization
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(BouncerThemeV2.shapes.extraLarge)
                .background(Color.Black)
                .border(1.dp, GlassBorderLight),
            contentAlignment = Alignment.Center
        ) {
            // 3D Pitch Background (Simulated with Gradient)
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(ChartCyan.copy(alpha = 0.1f), Color.Transparent))
                )
            )
            
            // "Review In Progress" Header
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .clip(BouncerThemeV2.shapes.extraSmall)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(1.dp, ChartCyan.copy(alpha = 0.3f), BouncerThemeV2.shapes.extraSmall)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(6.dp).alpha(neonPulse).background(ChartCyan, CircleShape).shadow(4.dp, spotColor = ChartCyan))
                    Text(
                        stringResource(R.string.drs_review_in_progress),
                        color = ChartCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Ball Tracking Visualization
            val stumpBrush = GoldGradient
            Canvas(modifier = Modifier.size(200.dp)) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                
                // Draw Stumps (Elite Metallic Gold)
                listOf(-20.dp, 0.dp, 20.dp).forEach { offset ->
                    drawRect(
                        brush = stumpBrush,
                        topLeft = Offset(centerX + offset.toPx() - 4.dp.toPx(), centerY - 40.dp.toPx()),
                        size = Size(8.dp.toPx(), 120.dp.toPx())
                    )
                }

                // Neon Trajectory Path
                val path = Path().apply {
                    moveTo(0f, size.height)
                    quadraticTo(centerX * 0.5f, centerY, centerX, centerY + 20.dp.toPx())
                }
                drawPath(path, ChartCyan, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, ChartCyan.copy(alpha = 0.2f * neonPulse), style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
            }

            // Bottom Labels (Pitching, Impact, Wickets)
            Column(
                modifier = Modifier.padding(16.dp).align(Alignment.BottomEnd),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DrsStatusRow("PITCHING", "IN LINE", ChartCyan)
                DrsStatusRow("IMPACT", "IN LINE", ChartCyan)
                DrsStatusRow("WICKETS", "HITTING", hierarchy.secondary.primary)
            }
        }

        // Dramatic Verdict
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (review.isSuccessful) "OUT" else "NOT OUT",
                color = hierarchy.secondary.primary,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-2).sp,
                modifier = Modifier.shadow(20.dp, spotColor = hierarchy.secondary.primary.copy(alpha = 0.4f))
            )
            Text(
                "UMPIRE'S DECISION OVERTURNED",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        // Delivery Metrics
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricGlassCard("Over", review.over, Icons.Default.TrackChanges, modifier = Modifier.weight(1f))
            MetricGlassCard("Decision", review.decision, Icons.Default.TrackChanges, modifier = Modifier.weight(1f))
            MetricGlassCard("Umpire", review.umpireName, Icons.Default.AutoAwesome, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DrsStatusRow(label: String, value: String, color: Color) {
    val colors = BouncerDesignSystem.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = colors.textPrimary.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MetricGlassCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Surface(
        modifier = modifier.height(100.dp),
        shape = BouncerThemeV2.shapes.large,
        color = colors.textPrimary.copy(alpha = 0.03f),
        border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.size(32.dp).background(colors.textPrimary.copy(alpha = 0.05f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = hierarchy.secondary.primary, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(label, color = colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

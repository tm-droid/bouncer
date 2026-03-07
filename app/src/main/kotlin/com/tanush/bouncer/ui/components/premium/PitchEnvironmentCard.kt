package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Technical Pitch & Environment Card.
 * Ported from MCP Predictive Forecast Mockup (code.html).
 * Displays real-time environmental data affecting play.
 */
@Composable
fun PitchEnvironmentCard(
    modifier: Modifier = Modifier,
    humidity: String = "65%",
    fatigue: String = "Low",
    dew: String = "High"
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(GlassSurface)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(20.dp))
            .padding(Spacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "PITCH ANALYSIS",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Icon(
                    Icons.Default.Landscape,
                    null,
                    tint = TextMuted,
                    modifier = Modifier.size(IconSize.xs)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EnvRow("Humidity", humidity, TextPrimary)
                EnvRow("Fatigue", fatigue, StatusSuccess)
                EnvRow("Dew Factor", dew, StatusLive)
            }
        }
    }
}

@Composable
private fun EnvRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

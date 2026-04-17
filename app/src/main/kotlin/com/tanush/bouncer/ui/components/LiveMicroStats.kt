package com.tanush.bouncer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.ui.theme.*

@Composable
fun LiveMicroStatsRow(
    batsman: ScrapedBatsman?,
    bowler: ScrapedBowler?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Striker Card
        MicroStatCard(
            label = "Striker",
            title = batsman?.name ?: "-",
            value = batsman?.runs ?: "0",
            subValue = "(${batsman?.balls ?: "0"})",
            icon = Icons.Default.SportsCricket,
            isPrimary = true,
            modifier = Modifier.weight(1f)
        )

        // Bowler Card - Using outlined circle to represent cricket ball
        MicroStatCard(
            label = "Bowler",
            title = bowler?.name ?: "-",
            value = "${bowler?.wickets ?: "0"}/${bowler?.runs ?: "0"}",
            subValue = "(${bowler?.overs ?: "0"})",
            icon = Icons.Outlined.Circle, // Cricket ball representation
            isPrimary = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MicroStatCard(
    label: String,
    title: String,
    value: String,
    subValue: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141414).copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = colors.glassBorderHighlight,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPrimary) hierarchy.secondary.primary else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        color = if (isPrimary) hierarchy.secondary.primary else Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subValue,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
    }
}

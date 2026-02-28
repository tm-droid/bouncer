package com.tanush.bouncer.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*

/**
 * Premium Insight Grid Card.
 * Ported from Legacy Suite & Enhanced for Broadcast Elite.
 */
@Composable
fun InsightGridCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    progress: Float? = null,
    accentColor: Color = ShinyGoldDeep
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label.uppercase(),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = value,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            
            Text(
                text = subValue,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            
            progress?.let {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = accentColor,
                    trackColor = Color.White.copy(alpha = 0.05f)
                )
            }
        }
    }
}

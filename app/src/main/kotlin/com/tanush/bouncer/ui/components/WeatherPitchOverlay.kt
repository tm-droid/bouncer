package com.tanush.bouncer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.ChartCyan
import com.tanush.bouncer.ui.theme.ChartGreen
import com.tanush.bouncer.ui.theme.ChartPurple

/**
 * WeatherPitchOverlay showing conditions icons
 * Displays weather and pitch conditions with relevant icons
 */
@Composable
fun WeatherPitchOverlay(
    weather: String = "Sunny",
    temperature: Int? = null,
    humidity: Int? = null,
    pitchType: String = "Balanced",
    windSpeed: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val weatherIcon = weatherIconFor(weather)
    val weatherColor = weatherColorFor(weather)
    val pitchColor = pitchColorFor(pitchType)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.surface1.copy(alpha = 0.8f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.glassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherBlock(
                weather = weather,
                icon = weatherIcon,
                iconTint = weatherColor,
                temperature = temperature
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(colors.glassBorder)
            )

            if (humidity != null) {
                ConditionChip(
                    icon = Icons.Default.WaterDrop,
                    iconTint = colors.statusInfo,
                    text = "$humidity%",
                    textColor = colors.textSecondary,
                    contentDescription = "Humidity"
                )
            }

            if (windSpeed != null) {
                ConditionChip(
                    icon = Icons.Default.Air,
                    iconTint = ChartCyan,
                    text = windSpeed,
                    textColor = colors.textSecondary,
                    contentDescription = "Wind"
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            PitchBadge(pitchType = pitchType, pitchColor = pitchColor)
        }
    }
}

private fun weatherIconFor(weather: String): ImageVector = when (weather.lowercase()) {
    "sunny", "clear" -> Icons.Default.WbSunny
    "cloudy", "overcast" -> Icons.Default.Cloud
    "rainy", "rain" -> Icons.Default.WaterDrop
    "partly cloudy" -> Icons.Default.FilterDrama
    else -> Icons.Default.WbSunny
}

@Composable
private fun weatherColorFor(weather: String): Color {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    return when (weather.lowercase()) {
        "sunny", "clear" -> hierarchy.secondary.primary
        "cloudy", "overcast" -> colors.textSecondary
        "rainy", "rain" -> colors.statusInfo
        else -> hierarchy.secondary.primary
    }
}

@Composable
private fun pitchColorFor(pitchType: String): Color {
    val colors = BouncerDesignSystem.colors
    return when (pitchType.lowercase()) {
        "batting", "flat" -> colors.statusSuccess
        "bowling", "green" -> ChartGreen
        "spin", "turning" -> ChartPurple
        "seaming" -> ChartCyan
        else -> colors.textSecondary
    }
}

@Composable
private fun WeatherBlock(
    weather: String,
    icon: ImageVector,
    iconTint: Color,
    temperature: Int?
) {
    val colors = BouncerDesignSystem.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Weather: $weather",
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = weather.uppercase(),
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            if (temperature != null) {
                Text(
                    text = "${temperature}°C",
                    color = colors.textSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun PitchBadge(pitchType: String, pitchColor: Color) {
    Box(
        modifier = Modifier
            .background(pitchColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, pitchColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = pitchType.uppercase(),
            color = pitchColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ConditionChip(
    icon: ImageVector,
    iconTint: Color,
    text: String,
    textColor: Color,
    contentDescription: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

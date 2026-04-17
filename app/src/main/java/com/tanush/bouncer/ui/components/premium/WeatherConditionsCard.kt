package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Weather conditions card showing impact on play
 *
 * TODO: Integration Requirements:
 * - Fetch real-time weather data from weather API
 * - Track conditions throughout the match day
 * - Show dew factor predictions (day-night matches)
 * - Display rain probability and radar
 * - Calculate impact on swing/spin
 * - Add weather change alerts
 * - Show historical weather at venue
 * - Track light meter readings
 * - Display sunset/sunrise for scheduling
 * - Cache weather data with short TTL
 */

data class MatchWeatherData(
    val temperature: Int,
    val humidity: Int,
    val windSpeed: Int,
    val windDirection: String,
    val condition: WeatherCondition,
    val dewFactor: Int,
    val visibility: String,
    val battingImpact: String,
    val bowlingImpact: String,
)

enum class WeatherCondition {
    SUNNY, CLOUDY, OVERCAST, PARTLY_CLOUDY, LIGHT_RAIN, DRIZZLE, CLEAR_NIGHT
}

@Composable
fun WeatherConditionsCard(
    weather: MatchWeatherData,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val infiniteTransition = rememberInfiniteTransition(label = "weather")

    val cloudOffset by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cloud",
    )

    val sunPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sun",
    )

    val weatherGradient = when (weather.condition) {
        WeatherCondition.SUNNY -> listOf(Color(0xFFFFB347), Color(0xFFFF8C00))
        WeatherCondition.CLOUDY, WeatherCondition.OVERCAST -> listOf(Color(0xFF607D8B), Color(0xFF455A64))
        WeatherCondition.PARTLY_CLOUDY -> listOf(Color(0xFF90CAF9), Color(0xFF42A5F5))
        WeatherCondition.LIGHT_RAIN, WeatherCondition.DRIZZLE -> listOf(Color(0xFF5C6BC0), Color(0xFF3949AB))
        WeatherCondition.CLEAR_NIGHT -> listOf(Color(0xFF1A237E), Color(0xFF0D47A1))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // Header with weather icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "MATCH CONDITIONS",
                        color = hierarchy.secondary.secondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = when (weather.condition) {
                            WeatherCondition.SUNNY -> "Bright & Sunny"
                            WeatherCondition.CLOUDY -> "Cloudy"
                            WeatherCondition.OVERCAST -> "Overcast"
                            WeatherCondition.PARTLY_CLOUDY -> "Partly Cloudy"
                            WeatherCondition.LIGHT_RAIN -> "Light Rain"
                            WeatherCondition.DRIZZLE -> "Drizzle"
                            WeatherCondition.CLEAR_NIGHT -> "Clear Night"
                        },
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Weather visual
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(colors = weatherGradient),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when (weather.condition) {
                        WeatherCondition.SUNNY -> {
                            Icon(
                                Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier
                                    .size(40.dp)
                                    .graphicsLayer {
                                        scaleX = sunPulse
                                        scaleY = sunPulse
                                    },
                            )
                        }
                        WeatherCondition.CLOUDY, WeatherCondition.OVERCAST, WeatherCondition.PARTLY_CLOUDY -> {
                            Icon(
                                Icons.Default.FilterDrama,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier
                                    .size(40.dp)
                                    .offset(x = cloudOffset.dp * 0.1f),
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Temperature
                WeatherStatItem(
                    value = "${weather.temperature}°C",
                    label = "Temperature",
                    icon = Icons.Default.WbSunny,
                    color = Color(0xFFFF9800),
                )

                // Humidity
                WeatherStatItem(
                    value = "${weather.humidity}%",
                    label = "Humidity",
                    icon = Icons.Default.WaterDrop,
                    color = Color(0xFF2196F3),
                )

                // Wind
                WeatherStatItem(
                    value = "${weather.windSpeed} km/h",
                    label = weather.windDirection,
                    icon = Icons.Default.Air,
                    color = Color(0xFF4CAF50),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dew factor gauge
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Dew Factor",
                        color = Color.Gray,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "${weather.dewFactor}/10",
                        color = when {
                            weather.dewFactor <= 3 -> colors.statusSuccess
                            weather.dewFactor <= 6 -> colors.statusWarning
                            else -> colors.statusLive
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dew gauge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2D2D44)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(weather.dewFactor / 10f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        colors.statusSuccess,
                                        colors.statusWarning,
                                        colors.statusLive,
                                    ),
                                    startX = 0f,
                                    endX = 500f,
                                ),
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Low", color = Color.Gray, fontSize = 10.sp)
                    Text("Medium", color = Color.Gray, fontSize = 10.sp)
                    Text("High", color = Color.Gray, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Impact analysis
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "PLAYING CONDITIONS IMPACT",
                    color = hierarchy.secondary.secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                // Batting impact
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sports,
                            contentDescription = "Batting",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Batting",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                        )
                    }
                    Text(
                        text = weather.battingImpact,
                        color = when {
                            weather.battingImpact.contains("Favor", ignoreCase = true) -> colors.statusSuccess
                            weather.battingImpact.contains("Difficult", ignoreCase = true) -> colors.statusLive
                            else -> colors.statusWarning
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                HorizontalDivider(color = Color(0xFF3D3D55), thickness = 1.dp)

                // Bowling impact
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚾",
                            fontSize = 16.sp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bowling",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                        )
                    }
                    Text(
                        text = weather.bowlingImpact,
                        color = when {
                            weather.bowlingImpact.contains("Favor", ignoreCase = true) -> colors.statusSuccess
                            weather.bowlingImpact.contains("Difficult", ignoreCase = true) -> colors.statusLive
                            else -> colors.statusWarning
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visibility
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Visibility: ",
                    color = Color.Gray,
                    fontSize = 12.sp,
                )
                Text(
                    text = weather.visibility,
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun WeatherStatItem(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp,
        )
    }
}

@Composable
fun WeatherConditionsCardPreview() {
    val colors = BouncerDesignSystem.colors
    WeatherConditionsCard(
        weather = MatchWeatherData(
            temperature = 32,
            humidity = 65,
            windSpeed = 12,
            windDirection = "SW",
            condition = WeatherCondition.PARTLY_CLOUDY,
            dewFactor = 4,
            visibility = "Excellent (10+ km)",
            battingImpact = "Favorable conditions",
            bowlingImpact = "Slight swing expected",
        ),
    )
}

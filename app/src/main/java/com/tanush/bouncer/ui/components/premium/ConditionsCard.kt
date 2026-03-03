package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.GlassSurface
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Conditions Card (Weather & Pitch) - Technical Meta-Data Readout.
 * Downgraded to MINIMAL importance to maintain visual hierarchy.
 */

data class CurrentWeatherInfo(
    val temperature: Int,
    val humidity: Int,
    val windSpeed: Int,
    val windDirection: String,
    val condition: String, // "Sunny", "Cloudy", "Overcast", "Rain", "Drizzle"
    val dewPoint: Int,
    val visibility: String,
)

data class PitchCondition(
    val type: String, // "Flat", "Green", "Dry", "Dusty", "Cracked"
    val pace: Int, // 1-10
    val bounce: Int, // 1-10
    val spin: Int, // 1-10
    val seam: Int, // 1-10
    val deterioration: String, // "Low", "Medium", "High"
)

@Composable
fun ConditionsCard(
    weather: CurrentWeatherInfo,
    pitch: PitchCondition,
    venue: String,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(weather, pitch) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing),
        )
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        importance = BouncerImportance.MINIMAL,
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header - Technical
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.02f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "GAME CONDITIONS",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = venue.uppercase(),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                WeatherEmoji(condition = weather.condition)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Weather & Pitch Type Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Weather Summary
                GlassSurface(
                    modifier = Modifier.weight(1f),
                    importance = BouncerImportance.MINIMAL,
                    cornerRadius = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TEMP", color = TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            Text("${weather.temperature}°C", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("HUMIDITY", color = TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            Text("${weather.humidity}%", color = colors.statusInfo, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Pitch Type
                GlassSurface(
                    modifier = Modifier.weight(1f),
                    importance = BouncerImportance.MINIMAL,
                    cornerRadius = 10.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("PITCH TYPE", color = TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Text(pitch.type.uppercase(), color = colors.statusSuccess, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Technical Pitch Stats
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "TECHNICAL PITCH ANALYSIS",
                    color = TextTertiary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                
                PitchCharacteristicRow(label = "PACE", value = pitch.pace, progress = animatedProgress.value, color = colors.statusInfo)
                PitchCharacteristicRow(label = "BOUNCE", value = pitch.bounce, progress = animatedProgress.value, color = colors.statusSuccess)
                PitchCharacteristicRow(label = "SPIN", value = pitch.spin, progress = animatedProgress.value, color = colors.goldPrimary.copy(alpha = 0.7f))
                PitchCharacteristicRow(label = "SEAM", value = pitch.seam, progress = animatedProgress.value, color = colors.statusLive)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Verdict
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val verdict = getPitchVerdict(pitch)
                Text(
                    text = "ADVISORY: $verdict".uppercase(),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun WeatherEmoji(condition: String) {
    val colors = BouncerDesignSystem.colors
    val icon = when (condition) {
        "Sunny" -> "☀️"
        "Cloudy" -> "⛅"
        "Overcast" -> "☁️"
        "Rain" -> "🌧️"
        "Drizzle" -> "🌦️"
        else -> "🌤️"
    }
    Text(text = icon, fontSize = 20.sp)
}

@Composable
private fun PitchCharacteristicRow(
    label: String,
    value: Int,
    progress: Float,
    color: Color,
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(50.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.03f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (value / 10f) * progress)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.6f)),
            )
        }

        Text(
            text = "$value/10",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(35.dp),
            textAlign = TextAlign.End
        )
    }
}

private fun getPitchVerdict(pitch: PitchCondition): String {
    return when {
        pitch.pace >= 7 && pitch.seam >= 6 -> "Fast Bowlers' Paradise"
        pitch.spin >= 7 -> "Spinner's Deck"
        pitch.bounce >= 8 -> "High Bounce Threat"
        pitch.pace <= 4 && pitch.spin >= 5 -> "Slow & Low"
        else -> "Balanced Contest"
    }
}

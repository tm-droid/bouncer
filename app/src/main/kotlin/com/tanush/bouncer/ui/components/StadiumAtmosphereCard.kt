package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import coil.request.ImageRequest
import com.tanush.bouncer.ui.theme.*
import kotlin.math.*

/**
 * Venue information data for Stadium Atmosphere Card
 */
data class StadiumVenueInfo(
    val name: String,
    val city: String,
    val country: String,
    val imageUrl: String? = null,
    val capacity: Int? = null,
    val attendance: Int? = null,
    val weather: StadiumWeatherInfo? = null,
    val pitchReport: StadiumPitchReport? = null,
    val stats: StadiumVenueStats? = null
)

data class StadiumWeatherInfo(
    val temperature: Int,
    val condition: StadiumWeatherCondition,
    val humidity: Int,
    val windSpeed: Int
)

enum class StadiumWeatherCondition {
    SUNNY, CLOUDY, PARTLY_CLOUDY, RAINY, OVERCAST, NIGHT
}

data class StadiumPitchReport(
    val type: StadiumPitchType,
    val behaviour: String,
    val avgFirstInningsScore: Int? = null,
    val avgSecondInningsScore: Int? = null
)

enum class StadiumPitchType {
    GREEN, DRY, FLAT, DUSTY, CRACKED, SPORTING
}

data class StadiumVenueStats(
    val matchesPlayed: Int,
    val avgFirstInningsScore: Int,
    val highestScore: Int,
    val lowestScore: Int,
    val battingFirst: Int, // % wins batting first
    val chasingSuccessRate: Int
)

/**
 * Premium Stadium Atmosphere Card
 * Shows venue info with immersive atmosphere effects
 */
@Composable
fun StadiumAtmosphereCard(
    venueInfo: StadiumVenueInfo,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stadium")
    
    // Crowd wave effect
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )
    
    // Light flicker effect
    val lightFlicker by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker"
    )
    
    // Ambient glow
    val ambientGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = ShinyGoldDeep.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Background stadium image or gradient
            if (venueInfo.imageUrl != null) {
                CoilImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(venueInfo.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = venueInfo.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .blur(2.dp),
                    contentScale = ContentScale.Crop
                )
                // Overlay gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.02f),
                                    Color.White.copy(alpha = 0.04f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            )
                        )
                )
            } else {
                // Animated stadium atmosphere background
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    
                    // Night sky gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0D1B2A),
                                Color(0xFF1B263B),
                                CardSurface
                            )
                        )
                    )
                    
                    // Stadium lights
                    val lightPositions = listOf(0.15f, 0.35f, 0.65f, 0.85f)
                    lightPositions.forEachIndexed { index, position ->
                        val lightX = width * position
                        val lightY = 25.dp.toPx()
                        val lightIntensity = ((sin((wavePhase + index * 0.5f).toDouble()) + 1) / 2 * 0.3f + 0.7f).toFloat()
                        
                        // Light glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.3f * lightIntensity * lightFlicker),
                                    Color.White.copy(alpha = 0.1f * lightIntensity),
                                    Color.Transparent
                                ),
                                center = Offset(lightX, lightY),
                                radius = 60.dp.toPx()
                            ),
                            radius = 60.dp.toPx(),
                            center = Offset(lightX, lightY)
                        )
                        
                        // Light source
                        drawCircle(
                            color = Color.White.copy(alpha = 0.9f * lightIntensity),
                            radius = 4.dp.toPx(),
                            center = Offset(lightX, lightY)
                        )
                    }
                    
                    // Crowd silhouette wave
                    val crowdPath = Path()
                    crowdPath.moveTo(0f, height * 0.6f)
                    
                    for (x in 0..width.toInt() step 10) {
                        val xNorm = x / width
                        val waveHeight = sin((xNorm * 4 * PI + wavePhase).toDouble()) * 8.dp.toPx()
                        val y = height * 0.65f + waveHeight.toFloat()
                        crowdPath.lineTo(x.toFloat(), y)
                    }
                    
                    crowdPath.lineTo(width, height)
                    crowdPath.lineTo(0f, height)
                    crowdPath.close()
                    
                    drawPath(
                        path = crowdPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2D3748).copy(alpha = 0.8f),
                                CardSurface
                            )
                        )
                    )
                }
            }
            
            // Content overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Venue name with location
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = ShinyGoldDeep,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = venueInfo.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Text(
                        text = "${venueInfo.city}, ${venueInfo.country}",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 26.dp)
                    )
                }
                
                // Weather and capacity row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Weather info
                    venueInfo.weather?.let { weather ->
                        WeatherInfoCard(
                            weather = weather,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Attendance
                    if (venueInfo.capacity != null || venueInfo.attendance != null) {
                        AttendanceCard(
                            capacity = venueInfo.capacity,
                            attendance = venueInfo.attendance,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Pitch report
                venueInfo.pitchReport?.let { pitch ->
                    PitchReportCard(pitchReport = pitch)
                }
                
                // Venue stats
                venueInfo.stats?.let { stats ->
                    VenueStatsSection(stats = stats)
                }
            }
        }
    }
}

@Composable
private fun WeatherInfoCard(
    weather: StadiumWeatherInfo,
    modifier: Modifier = Modifier
) {
    val weatherIcon = when (weather.condition) {
        StadiumWeatherCondition.SUNNY -> "☀️"
        StadiumWeatherCondition.CLOUDY -> "☁️"
        StadiumWeatherCondition.PARTLY_CLOUDY -> "⛅"
        StadiumWeatherCondition.RAINY -> "🌧️"
        StadiumWeatherCondition.OVERCAST -> "🌥️"
        StadiumWeatherCondition.NIGHT -> "🌙"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = CardBorder.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = CardBorder.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = weatherIcon,
                fontSize = 24.sp
            )
            
            Column {
                Text(
                    text = "${weather.temperature}°C",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "💧${weather.humidity}% 💨${weather.windSpeed}km/h",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun AttendanceCard(
    capacity: Int?,
    attendance: Int?,
    modifier: Modifier = Modifier
) {
    val fillPercentage = if (capacity != null && attendance != null) {
        (attendance.toFloat() / capacity * 100).toInt()
    } else null

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = ShinyGoldDeep.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = ShinyGoldDeep.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🏟️",
                    fontSize = 14.sp
                )
                Text(
                    text = "Attendance",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            
            if (attendance != null) {
                Text(
                    text = "${attendance / 1000}K",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            
            fillPercentage?.let { fill ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(
                                color = CardBorder.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fill / 100f)
                                .fillMaxHeight()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(ShinyGoldDeep, ShinyGoldMid)
                                    ),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                    Text(
                        text = "$fill%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = ShinyGoldDeep
                    )
                }
            }
        }
    }
}

@Composable
private fun PitchReportCard(
    pitchReport: StadiumPitchReport,
    modifier: Modifier = Modifier
) {
    val pitchColor = when (pitchReport.type) {
        StadiumPitchType.GREEN -> SuccessGreen
        StadiumPitchType.DRY -> WarningYellow
        StadiumPitchType.FLAT -> ChartBlue
        StadiumPitchType.DUSTY -> Color(0xFF8B7355)
        StadiumPitchType.CRACKED -> WicketRed
        StadiumPitchType.SPORTING -> ShinyGoldDeep
    }
    
    val pitchEmoji = when (pitchReport.type) {
        StadiumPitchType.GREEN -> "🟢"
        StadiumPitchType.DRY -> "🟡"
        StadiumPitchType.FLAT -> "🔵"
        StadiumPitchType.DUSTY -> "🟤"
        StadiumPitchType.CRACKED -> "🔴"
        StadiumPitchType.SPORTING -> "⭐"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = pitchColor.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = pitchColor.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pitchEmoji,
                    fontSize = 18.sp
                )
                Column {
                    Text(
                        text = "PITCH REPORT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = pitchReport.type.name.replace("_", " "),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = pitchColor
                    )
                }
            }
            
            Text(
                text = pitchReport.behaviour,
                fontSize = 11.sp,
                color = TextSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 160.dp)
            )
        }
    }
}

@Composable
private fun VenueStatsSection(
    stats: StadiumVenueStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "VENUE STATISTICS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = TextSecondary
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VenueStatItem(
                label = "Matches",
                value = stats.matchesPlayed.toString(),
                modifier = Modifier.weight(1f)
            )
            VenueStatItem(
                label = "Avg 1st Inn",
                value = stats.avgFirstInningsScore.toString(),
                modifier = Modifier.weight(1f)
            )
            VenueStatItem(
                label = "Bat First Wins",
                value = "${stats.battingFirst}%",
                modifier = Modifier.weight(1f)
            )
            VenueStatItem(
                label = "Chase Wins",
                value = "${stats.chasingSuccessRate}%",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VenueStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = CardBorder.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ShinyGoldDeep
            )
            Text(
                text = label,
                fontSize = 8.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * MEGA UI UPGRADE: Premium Match Header & Venue Components (v2.1).
 * Ultra-fidelity broadcast-grade components with cinematic aesthetics.
 */

data class VenueInfo(
    val name: String,
    val city: String,
    val country: String,
    val capacity: Int = 0,
    val imageUrl: String? = null,
    val timezone: String = ""
)

data class WeatherInfo(
    val condition: String,
    val temperature: Int,
    val humidity: Int,
    val windSpeed: Int,
    val chanceOfRain: Int = 0
)

@Composable
fun PremiumMatchHeader(
    team1Name: String,
    team1Logo: String?,
    team2Name: String,
    team2Logo: String?,
    matchType: String,
    seriesName: String,
    isLive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "header")
    
    val pulseScaleValue by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ShinyGoldDeep.copy(alpha = 0.12f),
                        BlackBackground
                    )
                )
            )
            .padding(top = 16.dp, bottom = 32.dp, start = 24.dp, end = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Series & Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = seriesName.uppercase(),
                        color = ShinyGoldDeep,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ELITE BROADCAST FEED",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                if (isLive) {
                    LiveIndicatorBadge(pulseScale = pulseScaleValue)
                }
            }
            
            // Teams row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamHeaderDisplay(name = team1Name, logoUrl = team1Logo, modifier = Modifier.weight(1f))
                
                // Technical VS badge
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.03f), CircleShape)
                        .border(0.5.dp, GoldHairline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                
                TeamHeaderDisplay(name = team2Name, logoUrl = team2Logo, modifier = Modifier.weight(1f))
            }
            
            // Match type chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = matchType.uppercase(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

@Composable
private fun TeamHeaderDisplay(
    name: String,
    logoUrl: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ambient Glow
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .blur(20.dp)
                    .alpha(0.2f)
                    .background(ShinyGoldDeep, CircleShape)
            )
            
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(ShinyGoldDeep, Color.Transparent)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (logoUrl != null) {
                    CoilImage(
                        model = logoUrl,
                        contentDescription = name,
                        modifier = Modifier.size(44.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = name.take(3).uppercase(),
                        color = ShinyGoldDeep,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
        
        Text(
            text = name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = (-0.5).sp
        )
    }
}

@Composable
private fun LiveIndicatorBadge(
    modifier: Modifier = Modifier,
    pulseScale: Float = 1f
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Red.copy(alpha = 0.1f))
            .border(0.5.dp, Color.Red.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .background(Color.Red, CircleShape)
        )
        
        Text(
            text = "LIVE",
            color = Color.Red,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
fun VenueCard(
    venue: VenueInfo,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(listOf(GoldHairline, Color.Transparent)),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        if (venue.imageUrl != null) {
            CoilImage(
                model = venue.imageUrl,
                contentDescription = venue.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .alpha(0.2f),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ShinyGoldDeep.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stadium,
                        contentDescription = null,
                        tint = ShinyGoldDeep,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column {
                    Text(
                        text = "STADIUM TELEMETRY",
                        color = ShinyGoldDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = venue.name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "${venue.city}, ${venue.country}",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            if (venue.capacity > 0 || venue.timezone.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (venue.capacity > 0) {
                        VenueInfoChip(label = "MAX CAPACITY", value = "${venue.capacity / 1000}K SPECTATORS")
                    }
                    if (venue.timezone.isNotEmpty()) {
                        VenueInfoChip(label = "TIMEZONE", value = venue.timezone)
                    }
                }
            }
        }
    }
}

@Composable
private fun VenueInfoChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun WeatherCard(
    weather: WeatherInfo,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(listOf(GoldHairline, Color.Transparent)),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val weatherIcon = when {
                    weather.condition.contains("rain", ignoreCase = true) -> Icons.Default.WaterDrop
                    weather.condition.contains("cloud", ignoreCase = true) -> Icons.Default.Cloud
                    else -> Icons.Default.WbSunny
                }
                
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = weatherIcon,
                        contentDescription = null,
                        tint = ShinyGoldDeep,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Column {
                    Text(
                        text = "ATMOSPHERIC DATA",
                        color = ShinyGoldDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${weather.temperature}°C",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = weather.condition.uppercase(),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WeatherStatItemCompact(Icons.Default.WaterDrop, "${weather.humidity}%", "HUMID")
                WeatherStatItemCompact(Icons.Default.Air, "${weather.windSpeed} km/h", "WIND")
            }
        }
    }
}

@Composable
private fun WeatherStatItemCompact(icon: ImageVector, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "$value $label", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Icon(imageVector = icon, contentDescription = null, tint = ShinyGoldDeep.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
    }
}

@Composable
fun PitchReportCard(
    pitchType: String,
    paceFriendly: Float,
    spinFriendly: Float,
    battingFriendly: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(listOf(GoldHairline, Color.Transparent)),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SURFACE ANALYSIS",
                        color = ShinyGoldDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = pitchType,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                
                Icon(Icons.Default.AutoAwesome, null, tint = ShinyGoldDeep.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
            }
            
            PitchVisualization(
                paceFriendly = paceFriendly,
                spinFriendly = spinFriendly,
                battingFriendly = battingFriendly
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PitchStatBlock(modifier = Modifier.weight(1f), label = "PACE", value = paceFriendly, color = Color(0xFF3B82F6))
                PitchStatBlock(modifier = Modifier.weight(1f), label = "SPIN", value = spinFriendly, color = Color(0xFF10B981))
                PitchStatBlock(modifier = Modifier.weight(1f), label = "BAT", value = battingFriendly, color = ShinyGoldDeep)
            }
        }
    }
}

@Composable
private fun PitchStatBlock(modifier: Modifier, label: String, value: Float, color: Color) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value)
                    .fillMaxHeight()
                    .background(color, CircleShape)
            )
        }
        Text(text = "${(value * 100).toInt()}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PitchVisualization(
    paceFriendly: Float,
    spinFriendly: Float,
    battingFriendly: Float
) {
    val animatedPace by animateFloatAsState(targetValue = paceFriendly, animationSpec = tween(1500), label = "pace")
    val animatedSpin by animateFloatAsState(targetValue = spinFriendly, animationSpec = tween(1500), label = "spin")
    val animatedBat by animateFloatAsState(targetValue = battingFriendly, animationSpec = tween(1500), label = "bat")
    
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxRadius = minOf(size.width, size.height) / 2 * 0.9f
        
        // Technical Grid
        for (i in 1..4) {
            drawCircle(
                color = Color.White.copy(alpha = 0.03f),
                radius = maxRadius * (i / 4f),
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        
        val angles = listOf(-90f, 30f, 150f)
        val values = listOf(animatedPace, animatedSpin, animatedBat)
        val colors = listOf(Color(0xFF3B82F6), Color(0xFF10B981), ShinyGoldDeep)
        
        val path = Path()
        angles.forEachIndexed { index, angle ->
            val rad = Math.toRadians(angle.toDouble())
            val radius = maxRadius * values[index]
            val x = centerX + (radius * cos(rad)).toFloat()
            val y = centerY + (radius * sin(rad)).toFloat()
            
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            
            // Axis Lines
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = Offset(centerX, centerY),
                end = Offset(centerX + (maxRadius * cos(rad)).toFloat(), centerY + (maxRadius * sin(rad)).toFloat()),
                strokeWidth = 1.dp.toPx()
            )
            
            drawCircle(color = colors[index], radius = 4.dp.toPx(), center = Offset(x, y))
        }
        path.close()
        
        drawPath(path = path, brush = Brush.radialGradient(listOf(ShinyGoldDeep.copy(alpha = 0.15f), Color.Transparent)), alpha = 0.5f)
        drawPath(path = path, color = ShinyGoldDeep, style = Stroke(width = 2.dp.toPx()))
    }
}

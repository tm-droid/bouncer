package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.GlassSurface
import com.tanush.bouncer.ui.theme.*

/**
 * Premium Sleek Hot Streak Card.
 * Refined for a high-stakes, professional aesthetic.
 * Updated: visual hierarchy is now STANDARD to avoid competing with live scores.
 */

data class HotStreakData(
    val playerName: String,
    val teamName: String,
    val streakType: StreakType,
    val streakLength: Int,
    val recentPerformances: List<StreakPerformance>,
    val currentForm: String,
    val statHighlight: String,
)

enum class StreakType {
    BATTING_FORM, BOWLING_FORM, WICKET_STREAK, CENTURY_STREAK, FIFTY_PLUS, ECONOMY_STREAK
}

data class StreakPerformance(
    val matchNumber: Int,
    val performance: String,
    val isHighlight: Boolean,
)

@Composable
fun HotStreakCard(
    streakData: HotStreakData,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hotstreak")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.02f,
        targetValue = 0.08f, // Subdued for STANDARD importance
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val streakEmoji = when (streakData.streakType) {
        StreakType.BATTING_FORM -> "🏏"
        StreakType.BOWLING_FORM -> "🎳"
        StreakType.WICKET_STREAK -> "🎯"
        StreakType.CENTURY_STREAK -> "💯"
        StreakType.FIFTY_PLUS -> "5️⃣0️⃣"
        StreakType.ECONOMY_STREAK -> "💰"
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        importance = BouncerImportance.STANDARD,
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header - Minimalist & Sleek
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(ShinyGoldDeep.copy(alpha = 0.05f), CircleShape)
                            .border(0.5.dp, GoldHairline.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = ShinyGoldDeep.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "HOT STREAK".uppercase(),
                            color = TextTertiary, // Use tertiary for headers in standard cards
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = streakData.playerName,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Streak counter - Precise Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ShinyGoldDeep.copy(alpha = 0.1f))
                        .border(0.5.dp, GoldHairline.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${streakData.streakLength}",
                            color = ShinyGoldDeep,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GAMES",
                            color = ShinyGoldDeep.copy(alpha = 0.6f),
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Central Stat Display - Standard importance
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                importance = BouncerImportance.STANDARD,
                cornerRadius = 10.dp,
                glowIntensity = glowAlpha
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = streakEmoji,
                        fontSize = 28.sp,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = streakData.streakType.name.replace("_", " "),
                        color = TextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = streakData.statHighlight,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )

                    Text(
                        text = streakData.currentForm.uppercase(),
                        color = SuccessGreen.copy(alpha = 0.9f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Recent performances - Horizontal List (Scrollable to prevent overlap)
            Text(
                text = "RECENT PERFORMANCES",
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()), // FIX OVERLAP: Allow scrolling if many chips
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                streakData.recentPerformances.forEach { perf ->
                    PerformanceChip(perf, modifier = Modifier.width(64.dp)) // FIX OVERLAP: Use fixed width instead of weight
                }
            }
        }
    }
}

@Composable
private fun PerformanceChip(
    performance: StreakPerformance,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (performance.isHighlight) ShinyGoldDeep.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.05f))
            .border(
                0.5.dp, 
                if (performance.isHighlight) GoldHairline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f), 
                RoundedCornerShape(6.dp)
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "M${performance.matchNumber}",
            color = TextTertiary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = performance.performance,
            color = if (performance.isHighlight) ShinyGoldDeep else TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

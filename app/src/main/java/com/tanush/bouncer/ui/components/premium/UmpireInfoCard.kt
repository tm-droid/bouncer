package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Umpire Info Card
 *
 * TODO: Integration Requirements:
 * - Fetch umpire data from match officials API
 * - Load umpire images from CDN
 * - Show umpire roles (on-field, TV, match referee)
 * - Display career statistics and experience
 * - Track decision accuracy ratings
 * - Show teams' historical record with umpire
 * - Add DRS overturn statistics
 * - Navigate to umpire profile on tap
 * - Show country and Elite Panel status
 * - Cache umpire data for match reference
 */

data class UmpireInfo(
    val name: String,
    val image: String,
    val role: String, // "On-field", "TV", "Third", "Match Referee"
    val country: String,
    val matchesUmpired: Int,
    val homeAdvantageRating: Float, // -1.0 to 1.0 (bias rating)
)

data class UmpireDecision(
    val overNumber: Float,
    val type: String, // "LBW", "Caught", "Run Out", "Stumped"
    val playerDismissed: String,
    val wasReviewed: Boolean,
    val reviewResult: String?, // "Upheld", "Overturned", null if not reviewed
)

@Composable
fun UmpireInfoCard(
    umpires: List<UmpireInfo>,
    decisions: List<UmpireDecision> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "umpire")

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Box {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.goldDark.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    start = Offset(size.width * shimmerOffset, 0f),
                    end = Offset(size.width * (shimmerOffset + 0.3f), size.height),
                )
                drawRect(brush = shimmerBrush)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = null,
                        tint = colors.goldDark,
                        modifier = Modifier.size(24.dp),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "MATCH OFFICIALS",
                        color = colors.goldDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Umpires horizontal scroll
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    umpires.forEach { umpire ->
                        UmpireCard(umpire = umpire)
                    }
                }

                // Decisions section
                if (decisions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "DECISIONS TODAY",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    decisions.take(5).forEach { decision ->
                        DecisionRow(decision = decision)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun UmpireCard(
    umpire: UmpireInfo,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    Column(
        modifier = modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.glassSurface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(colors.goldDark.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            CoilImage(
                model = umpire.image,
                contentDescription = umpire.name,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = umpire.name,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )

        Text(
            text = umpire.role,
            color = colors.goldDark,
            fontSize = 10.sp,
        )

        Text(
            text = umpire.country,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 9.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${umpire.matchesUmpired} matches",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun DecisionRow(
    decision: UmpireDecision,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val wasOverturned = decision.reviewResult == "Overturned"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (wasOverturned) {
                    colors.statusLive.copy(alpha = 0.1f)
                } else {
                    Color.White.copy(alpha = 0.03f)
                },
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Over number
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${decision.overNumber.toInt()}",
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = decision.playerDismissed,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = decision.type,
                color = colors.textSecondary,
                fontSize = 10.sp,
            )
        }

        if (decision.wasReviewed) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (wasOverturned) {
                            colors.statusLive.copy(alpha = 0.2f)
                        } else {
                            colors.statusSuccess.copy(alpha = 0.2f)
                        },
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = decision.reviewResult ?: "Reviewed",
                    color = if (wasOverturned) colors.statusLive else colors.statusSuccess,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

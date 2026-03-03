package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Match alerts card showing important notifications
 */

data class MatchAlert(
    val id: String,
    val type: AlertType,
    val title: String,
    val message: String,
    val timestamp: String,
    val isNew: Boolean = false,
)

enum class AlertType {
    WICKET, BOUNDARY, MILESTONE, RAIN, INJURY, REVIEW, POWERPLAY, DRINKS, STRATEGIC_TIMEOUT
}

@Composable
fun MatchAlertsCard(
    alerts: List<MatchAlert>,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesign.dimens
    val typography = BouncerDesign.typography

    val animatedProgress: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }

    LaunchedEffect(alerts) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "alerts")
    val bellShake by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shake",
    )

    val newAlerts = alerts.count { it.isNew }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.radius.xl),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = dimens.elevation.level3),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacing.lg),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(dimens.iconSize.hero)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(colors.goldPrimary, colors.goldPrimary.copy(alpha = 0.7f)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier
                                .size(dimens.iconSize.lg)
                                .graphicsLayer {
                                    rotationZ = if (newAlerts > 0) bellShake else 0f
                                },
                        )

                        if (newAlerts > 0) {
                            Box(
                                modifier = Modifier
                                    .size(dimens.spacing.md)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .clip(CircleShape)
                                    .background(colors.statusLive),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$newAlerts",
                                    color = Color.White,
                                    style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(dimens.spacing.md))

                    Column {
                        Text(
                            text = "MATCH ALERTS",
                            color = colors.goldPrimary,
                            style = typography.labelLarge,
                        )
                        Text(
                            text = "${alerts.size} notifications",
                            color = colors.textMuted,
                            style = typography.labelSmall,
                        )
                    }
                }

                if (newAlerts > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.statusInfo.copy(alpha = 0.2f))
                            .border(1.dp, colors.statusInfo, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "$newAlerts NEW",
                            color = colors.statusInfo,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Alerts list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                alerts.take(5).forEachIndexed { index, alert ->
                    val alertAlpha = (animatedProgress.value * 5 - index).coerceIn(0f, 1f)

                    AlertItem(
                        alert = alert,
                        alpha = alertAlpha,
                    )
                }
            }

            if (alerts.size > 5) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "View ${alerts.size - 5} more alerts →",
                    color = GoldPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun AlertItem(
    alert: MatchAlert,
    alpha: Float,
) {
    val colors = BouncerDesignSystem.colors
    val alertConfig = when (alert.type) {
        AlertType.WICKET -> AlertConfig(colors.statusLive, "W", "Wicket")
        AlertType.BOUNDARY -> AlertConfig(BouncerDesignSystem.colors.statusWarning, "B", "Boundary")
        AlertType.MILESTONE -> AlertConfig(colors.goldPrimary, "M", "Milestone")
        AlertType.RAIN -> AlertConfig(BouncerDesignSystem.colors.statusInfo, "R", "Weather")
        AlertType.INJURY -> AlertConfig(colors.statusWarning, "I", "Injury")
        AlertType.REVIEW -> AlertConfig(Color(0xFF9C27B0), "V", "Review")
        AlertType.POWERPLAY -> AlertConfig(colors.statusSuccess, "P", "Powerplay")
        AlertType.DRINKS -> AlertConfig(Color(0xFF00BCD4), "D", "Drinks")
        AlertType.STRATEGIC_TIMEOUT -> AlertConfig(Color(0xFF607D8B), "T", "Timeout")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (alert.isNew) {
                    alertConfig.color.copy(alpha = 0.15f)
                } else {
                    Color(0xFF2D2D44)
                },
            )
            .then(
                if (alert.isNew) {
                    Modifier.border(1.dp, alertConfig.color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(alertConfig.color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = alertConfig.emoji,
                fontSize = 18.sp,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = alert.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    if (alert.isNew) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.statusInfo)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "NEW",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Text(
                    text = alert.timestamp,
                    color = Color.Gray,
                    fontSize = 10.sp,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = alert.message,
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = alertConfig.label,
                color = alertConfig.color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private data class AlertConfig(
    val color: Color,
    val emoji: String,
    val label: String,
)

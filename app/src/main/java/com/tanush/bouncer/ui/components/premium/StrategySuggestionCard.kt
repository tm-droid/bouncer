package com.tanush.bouncer.ui.components.premium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.ChartBlue
import com.tanush.bouncer.ui.theme.ShinyGoldLight
import com.tanush.bouncer.ui.theme.SuccessGreen
import com.tanush.bouncer.ui.theme.WarningYellow
import com.tanush.bouncer.ui.theme.WicketRed
import kotlin.math.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Strategy suggestion card showing AI-powered recommendations
 *
 * TODO: API Integration
 * - Integrate AI/ML model for generating situation-aware strategy suggestions
 * - Connect to match context service for real-time situational analysis
 * - Implement confidence scoring based on historical accuracy
 * - Add feedback loop to improve suggestion quality over time
 *
 * TODO: Features
 * - Add strategy category filtering (batting, bowling, field placement)
 * - Implement user feedback mechanism for suggestion rating
 * - Show historical success rate for similar suggestions
 * - Add coach's notes/override capability
 *
 * TODO: UI/UX
 * - Add loading state while AI generates suggestions
 * - Implement priority sorting based on match urgency
 * - Add dismiss/save functionality for suggestions
 */

data class StrategySuggestion(
    val title: String,
    val description: String,
    val confidence: Float, // 0-100
    val priority: StrategyPriority,
    val category: StrategyCategory,
    val expectedImpact: String,
)

enum class StrategyPriority {
    HIGH, MEDIUM, LOW
}

enum class StrategyCategory {
    BATTING, BOWLING, FIELDING, GENERAL
}

@Composable
fun StrategySuggestionCard(
    teamName: String,
    situation: String,
    suggestions: List<StrategySuggestion>,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val animatedProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "ai")
    val aiGlow by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    LaunchedEffect(suggestions) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        )
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
            // Header with AI badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF9C27B0).copy(alpha = aiGlow),
                                        Color(0xFF673AB7).copy(alpha = aiGlow),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "AI STRATEGY",
                                color = hierarchy.secondary.secondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF9C27B0).copy(alpha = 0.3f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "BETA",
                                    color = Color(0xFF9C27B0),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Text(
                            text = teamName,
                            color = colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current situation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2D2D44))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Analysis",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "CURRENT SITUATION",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = situation,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Suggestions list
            Text(
                text = "RECOMMENDATIONS",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                suggestions.take(4).forEachIndexed { index, suggestion ->
                    val suggestionAlpha = (animatedProgress.value * 4 - index).coerceIn(0f, 1f)

                    SuggestionItem(
                        suggestion = suggestion,
                        alpha = suggestionAlpha,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Disclaimer
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "AI suggestions are based on statistical analysis and may not account for all match conditions.",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: StrategySuggestion,
    alpha: Float,
) {
    val colors = BouncerDesignSystem.colors
    val priorityColor = when (suggestion.priority) {
        StrategyPriority.HIGH -> colors.statusLive
        StrategyPriority.MEDIUM -> colors.statusWarning
        StrategyPriority.LOW -> colors.statusSuccess
    }

    val categoryEmoji = when (suggestion.category) {
        StrategyCategory.BATTING -> "🏏"
        StrategyCategory.BOWLING -> "⚾"
        StrategyCategory.FIELDING -> "🧤"
        StrategyCategory.GENERAL -> "📋"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2D2D44))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = categoryEmoji,
                    fontSize = 20.sp,
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = suggestion.title,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = suggestion.description,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            // Priority badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(priorityColor.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = suggestion.priority.name,
                    color = priorityColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Confidence bar and expected impact
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Confidence:",
                    color = Color.Gray,
                    fontSize = 10.sp,
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF3D3D55)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(suggestion.confidence / 100f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    suggestion.confidence >= 80 -> colors.statusSuccess
                                    suggestion.confidence >= 60 -> colors.statusInfo
                                    else -> colors.statusWarning
                                },
                            ),
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "${suggestion.confidence.toInt()}%",
                    color = Color.Gray,
                    fontSize = 10.sp,
                )
            }

            Text(
                text = suggestion.expectedImpact,
                color = colors.statusSuccess,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun StrategySuggestionCardPreview() {
    val colors = BouncerDesignSystem.colors
    val suggestions = listOf(
        StrategySuggestion(
            title = "Bring in spinner from pavilion end",
            description = "Left-hander struggles against off-spin. Economy rate 4.5 vs spinners this match.",
            confidence = 85f,
            priority = StrategyPriority.HIGH,
            category = StrategyCategory.BOWLING,
            expectedImpact = "+12% wicket probability",
        ),
        StrategySuggestion(
            title = "Move third man to slip",
            description = "New ball due. Batsman has edged 3 times in last 10 balls.",
            confidence = 72f,
            priority = StrategyPriority.MEDIUM,
            category = StrategyCategory.FIELDING,
            expectedImpact = "+8% catch opportunity",
        ),
        StrategySuggestion(
            title = "Target yorkers at death",
            description = "Batsman strike rate drops 40% against yorkers in overs 18-20.",
            confidence = 78f,
            priority = StrategyPriority.HIGH,
            category = StrategyCategory.BOWLING,
            expectedImpact = "-1.5 run rate",
        ),
        StrategySuggestion(
            title = "Consider DRS for next appeal",
            description = "Umpire has given 2 incorrect decisions. 1 review remaining.",
            confidence = 55f,
            priority = StrategyPriority.LOW,
            category = StrategyCategory.GENERAL,
            expectedImpact = "Save review",
        ),
    )

    StrategySuggestionCard(
        teamName = "India",
        situation = "Chasing 186, need 48 from 30 balls with 6 wickets in hand",
        suggestions = suggestions,
    )
}

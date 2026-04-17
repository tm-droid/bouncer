package com.tanush.bouncer.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.GoldPrimary
import com.tanush.bouncer.ui.theme.TeamPrimaryColors
import com.tanush.bouncer.ui.theme.bouncerClickable

/**
 * Heavy glass card for hero sections (e.g. Match Summary).
 * Features 40px blur, heavier background, and inset gold shadow.
 */
@Composable
fun GlassHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val shapes = BouncerDesignSystem.shapes
    
    Box(
        modifier = modifier
            .clip(shapes.glassCardHero)
            .background(colors.glassSurface)
            .drawBehind {
                // Inset gold shadow effect
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            hierarchy.secondary.primary.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2, size.height),
                        radius = size.width
                    )
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        hierarchy.secondary.primary.copy(alpha = 0.4f),
                        hierarchy.secondary.primary.copy(alpha = 0.1f)
                    )
                ),
                shape = shapes.glassCardHero
            )
    ) {
        // Noise texture
        NoiseOverlay(modifier = Modifier.matchParentSize(), opacity = 0.06f)
        
        Box(
            modifier = Modifier.padding(BouncerDesignSystem.dimens.spacing.lg),
            content = content
        )
    }
}

/**
 * Filter chip with glass background and gold active state.
 */
@Composable
fun GlassFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    
    val shape = RoundedCornerShape(percent = 50)
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) hierarchy.secondary.primary.copy(alpha = 0.2f) 
                else colors.glassSurface
            )
            .border(
                width = 1.dp,
                color = if (selected) hierarchy.secondary.primary.copy(alpha = 0.5f) 
                        else colors.glassBorder,
                shape = shape
            )
            .bouncerClickable(onClick = onClick)
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.labelSmall,
            color = if (selected) hierarchy.secondary.primary else colors.textSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * Modifier for batting table rows to indicate the current striker.
 * Adds a team-colored gradient and gold inset border.
 */
fun Modifier.strikeRow(
    teamColor: Color
): Modifier = this.drawBehind {
    // Team background gradient
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                teamColor.copy(alpha = 0.15f),
                Color.Transparent
            )
        )
    )
    
    // Gold left border (inset effect)
    drawRect(
        color = GoldPrimary,
        topLeft = Offset(0f, 0f),
        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
    )
}

/**
 * Team flag background gradient for hero cards with floating large emojis.
 */
@Composable
fun TeamFlagBackground(
    leftTeamCode: String,
    rightTeamCode: String,
    modifier: Modifier = Modifier
) {
    val leftColor = TeamPrimaryColors.getTeamColor(leftTeamCode)
    val rightColor = TeamPrimaryColors.getTeamColor(rightTeamCode)
    val leftEmoji = TeamPrimaryColors.getTeamEmoji(leftTeamCode)
    val rightEmoji = TeamPrimaryColors.getTeamEmoji(rightTeamCode)

    Row(modifier = modifier.fillMaxSize()) {
        // Left Team Background
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            leftColor.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1f, 1f)
                    )
                )
        ) {
            Text(
                text = leftEmoji,
                fontSize = 120.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 10.dp, y = 10.dp)
                    .graphicsLayer { 
                        alpha = 0.3f
                        scaleX = 1.2f
                        scaleY = 1.2f
                    }
            )
        }

        // Vertical Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(BouncerDesignSystem.colors.glassSurface)
        )

        // Right Team Background
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            rightColor.copy(alpha = 0.25f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1f, 1f)
                    )
                )
        ) {
            Text(
                text = rightEmoji,
                fontSize = 120.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-10).dp, y = (-10).dp)
                    .graphicsLayer { 
                        alpha = 0.3f
                        scaleX = 1.2f
                        scaleY = 1.2f
                    }
            )
        }
    }
}

package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * Premium section divider with animated shimmer and title
 */
@Composable
fun SectionDivider(
    title: String,
    modifier: Modifier = Modifier,
    showShimmer: Boolean = true
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Decorative Dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(hierarchy.secondary.primary, CircleShape)
        )
        
        // Title
        Text(
            text = title.uppercase(),
            color = hierarchy.secondary.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        
        // Extended gradient line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            hierarchy.secondary.primary.copy(alpha = 0.6f),
                            hierarchy.secondary.primary.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * A premium section divider with gold gradient and optional title
 */
@Composable
fun GoldSectionDivider(
    modifier: Modifier = Modifier,
    title: String? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            hierarchy.secondary.primary.copy(alpha = 0.5f)
                        )
                    )
                )
        )
        
        if (title != null) {
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = title.uppercase(),
                color = hierarchy.secondary.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.5.sp
            )
            Spacer(modifier = Modifier.width(20.dp))
        }
        
        // Right line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            hierarchy.secondary.primary.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Premium live indicator badge with pulsing animation
 */
@Composable
fun LiveBadge(
    modifier: Modifier = Modifier,
    text: String = "LIVE"
) {
    val colors = BouncerDesignSystem.colors
    val glowAlpha = 0.6f

    Surface(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = BouncerThemeV2.shapes.medium,
                spotColor = colors.statusLive.copy(alpha = glowAlpha)
            ),
        color = colors.statusLive,
        shape = BouncerThemeV2.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.White, CircleShape)
                    .shadow(4.dp, CircleShape, spotColor = Color.White)
            )
            Text(
                text = text.uppercase(),
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/**
 * Premium progress indicator with gold styling
 */
@Composable
fun GoldProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    backgroundColor: Color = GlassSurface,
    showLabel: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(modifier = modifier) {
        if (showLabel) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.common_win_probability),
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = hierarchy.secondary.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(backgroundColor)
                .border(0.5.dp, colors.glassBorder.copy(alpha = 0.5f), RoundedCornerShape(50))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary, hierarchy.secondary.secondary)
                        ),
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}

@Composable
fun GlowingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = GoldPrimary,
    backgroundColor: Color = GlassSurface,
    content: @Composable () -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val glowAlpha = 0.45f

    Box(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                spotColor = glowColor.copy(alpha = glowAlpha)
            )
            .background(backgroundColor, CircleShape)
            .border(1.dp, glowColor.copy(alpha = 0.4f), CircleShape)
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription as semanticContentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.rememberHapticFeedback

@Composable
fun StatusChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val haptic = rememberHapticFeedback()
    
    val animatedElevation by animateFloatAsState(
        targetValue = if (isSelected) 12f else 4f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "chipElevation"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "chipShimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chipShimmer"
    )

    val backgroundBrush = if (isSelected) {
        Brush.linearGradient(
            colors = listOf(
                hierarchy.secondary.secondary.copy(alpha = shimmerAlpha),
                hierarchy.secondary.primary,
                hierarchy.secondary.primary
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                GlassSurface,
                GlassSurface
            )
        )
    }

    Box(
        modifier = Modifier
            .shadow(
                elevation = animatedElevation.dp,
                shape = CircleShape,
                spotColor = if (isSelected) colors.goldPrimary.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.3f),
                ambientColor = if (isSelected) colors.goldPrimary.copy(alpha = 0.25f) else Color.Transparent
            )
            .clip(CircleShape)
            .background(backgroundBrush)
            .then(
                if (!isSelected) Modifier.border(
                    1.dp, 
                    Brush.linearGradient(listOf(
                        colors.glassBorder.copy(alpha = 0.4f), 
                        colors.glassBorder.copy(alpha = 0.2f),
                        Color.Transparent
                    )), 
                    CircleShape
                )
                else Modifier.border(
                    1.5.dp, 
                    Brush.linearGradient(
                        colors = listOf(
                            TextSecondary,
                            hierarchy.secondary.secondary.copy(alpha = 0.5f),
                            hierarchy.secondary.primary.copy(alpha = 0.2f)
                        )
                    ), 
                    CircleShape
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    bounded = true,
                    color = if (isSelected) Color.Black.copy(alpha = 0.3f) else colors.goldPrimary.copy(alpha = 0.3f)
                )
            ) {
                haptic.click()
                onClick()
            }
            .semantics {
                semanticContentDescription = "Filter by $text"
                selected = isSelected
            }
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = text.uppercase(),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
            color = if (isSelected) colors.backgroundBase else colors.textSecondary,
            letterSpacing = 1.sp
        )
    }
}

/**
 * iOS-inspired Animated Checkmark component with spring animation
 * Mimics the CategoryChipView from iOS template
 */
@Composable
fun AnimatedCheckmark(
    isChecked: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val scale by animateFloatAsState(
        targetValue = if (isChecked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkmarkScale"
    )
    
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(
                if (isChecked) colors.statusSuccess.copy(alpha = 0.8f) 
                else GlassSurface
            )
            .border(
                width = 1.5.dp,
                color = if (isChecked) colors.statusSuccess else GlassBorder,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isChecked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(12.dp)
                    .scale(scale)
            )
        }
    }
}

@Composable
fun CategoryChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val haptic = rememberHapticFeedback()
    
    val animatedElevation by animateFloatAsState(
        targetValue = if (isSelected) 8f else 2f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "categoryElevation"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "categoryShimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "categoryShimmer"
    )

    // Material 3 inspired gradient backgrounds
    val backgroundBrush = if (isSelected) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF3B82F6).copy(alpha = 0.75f), // Blue
                Color(0xFF10B981).copy(alpha = 0.7f * shimmerAlpha)  // Green
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                GlassSurface,
                GlassSurface
            )
        )
    }

    Row(
        modifier = Modifier
            .shadow(
                elevation = animatedElevation.dp,
                shape = CircleShape,
                spotColor = if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.5f) else Color.Transparent,
                ambientColor = if (isSelected) Color(0xFF10B981).copy(alpha = 0.2f) else Color.Transparent
            )
            .clip(CircleShape)
            .background(backgroundBrush)
            .border(
                width = if (isSelected) 0.8.dp else 0.5.dp,
                color = if (isSelected) TextMuted else colors.glassBorder.copy(alpha = 0.25f),
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    bounded = true,
                    color = if (isSelected) TextMuted else Color(0xFF3B82F6).copy(alpha = 0.3f)
                )
            ) {
                haptic.click()
                onClick()
            }
            .semantics {
                semanticContentDescription = "Category: $text"
                selected = isSelected
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Material 3 style animated checkmark
        AnimatedCheckmark(isChecked = isSelected)
        
        Spacer(modifier = Modifier.width(7.dp))
        
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else TextSecondary,
            letterSpacing = 0.3.sp
        )
    }
}

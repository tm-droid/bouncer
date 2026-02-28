package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.rememberHapticFeedback

/**
 * Material 3 Enhanced Button Components
 * 
 * Provides native Android button styles with:
 * - Material 3 compliant ripples and state layers
 * - Smooth spring animations
 * - Proper elevation and shadows
 * - Accessibility support
 */

/**
 * Primary elevated button with Material 3 styling
 */
@Composable
fun PrimaryElevatedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        ),
        label = "buttonScale"
    )
    
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 2f else 6f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "buttonElevation"
    )
    
    Button(
        onClick = {
            if (!loading) {
                haptic.click()
                onClick()
            }
        },
        modifier = modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = ShinyGoldDeep.copy(alpha = 0.4f),
                ambientColor = ShinyGoldDeep.copy(alpha = 0.15f)
            )
            .semantics {
                contentDescription = text
            },
        enabled = enabled && !loading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ShinyGoldDeep,
            contentColor = BlackBackground,
            disabledContainerColor = ShinyGoldDim.copy(alpha = 0.5f),
            disabledContentColor = BlackBackground.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp,
            disabledElevation = 0.dp
        ),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = BlackBackground,
                strokeWidth = 2.dp
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Outlined button with Material 3 styling
 */
@Composable
fun OutlinedMaterialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        ),
        label = "outlinedButtonScale"
    )
    
    OutlinedButton(
        onClick = {
            haptic.click()
            onClick()
        },
        modifier = modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                contentDescription = text
            },
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = ShinyGoldDeep,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TextTertiary
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = if (enabled) {
                    listOf(
                        ShinyGoldDeep.copy(alpha = 0.8f),
                        ShinyGoldMid.copy(alpha = 0.6f)
                    )
                } else {
                    listOf(TextTertiary, TextTertiary)
                }
            )
        ),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Glass morphic button with elevated blur effect
 */
@Composable
fun GlassMorphicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = ShinyGoldDeep
) {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 400f
        ),
        label = "glassButtonScale"
    )
    
    Box(
        modifier = modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isPressed) 4.dp else 8.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = tint.copy(alpha = 0.3f),
                ambientColor = tint.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.06f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = tint.copy(alpha = 0.2f)
                )
            ) {
                haptic.click()
                onClick()
            }
            .semantics {
                contentDescription = text
            }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Floating action button with enhanced Material 3 styling
 */
@Composable
fun EnhancedFAB(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    containerColor: Color = ShinyGoldDeep,
    contentColor: Color = BlackBackground,
    elevation: Dp = 12.dp
) {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        ),
        label = "fabScale"
    )
    
    val fabElevation by animateFloatAsState(
        targetValue = if (isPressed) elevation.value * 0.6f else elevation.value,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "fabElevation"
    )
    
    FloatingActionButton(
        onClick = {
            haptic.click()
            onClick()
        },
        modifier = modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                this.contentDescription = contentDescription
            },
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = fabElevation.dp,
            pressedElevation = (fabElevation * 0.5f).dp
        ),
        shape = CircleShape,
        interactionSource = interactionSource
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}

package com.tanush.bouncer.ui.accessibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlin.math.roundToInt
import io.sentry.Sentry

/**
 * Visual Accessibility Utilities for Bouncer Cricket App
 * 
 * Provides high contrast modes, color-blind friendly palettes,
 * text scaling, and WCAG compliance utilities.
 */

// ==========================================
// Color-Blind Friendly Palettes
// ==========================================

/**
 * Color-blind friendly color palettes optimized for various types of color vision deficiency
 */
object ColorBlindPalette {
    
    /**
     * Standard accessible colors usable by most users
     */
    object Universal {
        val Blue = Color(0xFF0077BB)
        val Orange = Color(0xFFEE7733)
        val Yellow = Color(0xFFCCBB44)
        val Green = Color(0xFF009988)
        val Cyan = Color(0xFF33BBEE)
        val Magenta = Color(0xFFEE3377)
        val Red = Color(0xFFCC3311)
        val Grey = Color(0xFFBBBBBB)
        
        // Cricket-specific mappings
        val TeamWinning = Green
        val TeamLosing = Orange
        val LiveMatch = Blue
        val CompletedMatch = Grey
        val Boundary = Yellow
        val Wicket = Red
        val Run = Cyan
        val DotBall = Grey
    }
    
    /**
     * Protanopia (Red-blind) optimized palette
     */
    object Protanopia {
        val Primary = Color(0xFF0077BB)      // Blue
        val Secondary = Color(0xFFCCBB44)    // Yellow
        val Accent = Color(0xFF33BBEE)       // Cyan
        val Warning = Color(0xFFEE7733)      // Orange
        val Success = Color(0xFF009988)      // Teal
        val Error = Color(0xFFCC7722)        // Brown-Orange (avoid pure red)
        
        // Cricket-specific
        val TeamA = Primary
        val TeamB = Secondary
        val LiveIndicator = Accent
        val WicketIndicator = Warning
        val BoundaryFour = Color(0xFF66AACC)
        val BoundarySix = Secondary
    }
    
    /**
     * Deuteranopia (Green-blind) optimized palette
     */
    object Deuteranopia {
        val Primary = Color(0xFF0077BB)      // Blue
        val Secondary = Color(0xFFCCBB44)    // Yellow
        val Accent = Color(0xFFEE7733)       // Orange
        val Warning = Color(0xFFCC3311)      // Red-Orange
        val Success = Color(0xFF33BBEE)      // Cyan (avoid green)
        val Error = Color(0xFFCC3311)        // Red-Orange
        
        // Cricket-specific
        val TeamA = Primary
        val TeamB = Secondary
        val LiveIndicator = Color(0xFF0099DD)
        val WicketIndicator = Accent
        val BoundaryFour = Secondary
        val BoundarySix = Color(0xFFFF9944)
    }
    
    /**
     * Tritanopia (Blue-blind) optimized palette
     */
    object Tritanopia {
        val Primary = Color(0xFFCC3311)      // Red
        val Secondary = Color(0xFF009988)    // Teal
        val Accent = Color(0xFFEE7733)       // Orange
        val Warning = Color(0xFFCCBB44)      // Yellow
        val Success = Secondary
        val Error = Primary
        
        // Cricket-specific
        val TeamA = Primary
        val TeamB = Secondary
        val LiveIndicator = Accent
        val WicketIndicator = Primary
        val BoundaryFour = Warning
        val BoundarySix = Secondary
    }
    
    /**
     * High contrast monochromatic palette for maximum visibility
     */
    object HighContrastMono {
        val Background = Color.Black
        val Surface = Color(0xFF1A1A1A)
        val OnSurface = Color.White
        val Primary = Color.White
        val Secondary = Color(0xFFCCCCCC)
        val Accent = Color(0xFFFFFFFF)
        val Border = Color.White
        val Muted = Color(0xFF888888)
    }
}

/**
 * Current color blind mode preference
 */
enum class ColorBlindMode {
    NONE,
    PROTANOPIA,
    DEUTERANOPIA,
    TRITANOPIA,
    HIGH_CONTRAST_MONO
}

/**
 * Get appropriate colors based on color blind mode
 */
@Composable
fun getAccessibleColor(
    mode: ColorBlindMode,
    colorType: AccessibleColorType
): Color {
    return when (mode) {
        ColorBlindMode.NONE -> when (colorType) {
            AccessibleColorType.PRIMARY -> ColorBlindPalette.Universal.Blue
            AccessibleColorType.SUCCESS -> ColorBlindPalette.Universal.Green
            AccessibleColorType.ERROR -> ColorBlindPalette.Universal.Red
            AccessibleColorType.WARNING -> ColorBlindPalette.Universal.Orange
            AccessibleColorType.LIVE -> ColorBlindPalette.Universal.LiveMatch
            AccessibleColorType.WICKET -> ColorBlindPalette.Universal.Wicket
            AccessibleColorType.BOUNDARY -> ColorBlindPalette.Universal.Boundary
        }
        ColorBlindMode.PROTANOPIA -> when (colorType) {
            AccessibleColorType.PRIMARY -> ColorBlindPalette.Protanopia.Primary
            AccessibleColorType.SUCCESS -> ColorBlindPalette.Protanopia.Success
            AccessibleColorType.ERROR -> ColorBlindPalette.Protanopia.Error
            AccessibleColorType.WARNING -> ColorBlindPalette.Protanopia.Warning
            AccessibleColorType.LIVE -> ColorBlindPalette.Protanopia.LiveIndicator
            AccessibleColorType.WICKET -> ColorBlindPalette.Protanopia.WicketIndicator
            AccessibleColorType.BOUNDARY -> ColorBlindPalette.Protanopia.BoundarySix
        }
        ColorBlindMode.DEUTERANOPIA -> when (colorType) {
            AccessibleColorType.PRIMARY -> ColorBlindPalette.Deuteranopia.Primary
            AccessibleColorType.SUCCESS -> ColorBlindPalette.Deuteranopia.Success
            AccessibleColorType.ERROR -> ColorBlindPalette.Deuteranopia.Error
            AccessibleColorType.WARNING -> ColorBlindPalette.Deuteranopia.Warning
            AccessibleColorType.LIVE -> ColorBlindPalette.Deuteranopia.LiveIndicator
            AccessibleColorType.WICKET -> ColorBlindPalette.Deuteranopia.WicketIndicator
            AccessibleColorType.BOUNDARY -> ColorBlindPalette.Deuteranopia.BoundarySix
        }
        ColorBlindMode.TRITANOPIA -> when (colorType) {
            AccessibleColorType.PRIMARY -> ColorBlindPalette.Tritanopia.Primary
            AccessibleColorType.SUCCESS -> ColorBlindPalette.Tritanopia.Success
            AccessibleColorType.ERROR -> ColorBlindPalette.Tritanopia.Error
            AccessibleColorType.WARNING -> ColorBlindPalette.Tritanopia.Warning
            AccessibleColorType.LIVE -> ColorBlindPalette.Tritanopia.LiveIndicator
            AccessibleColorType.WICKET -> ColorBlindPalette.Tritanopia.WicketIndicator
            AccessibleColorType.BOUNDARY -> ColorBlindPalette.Tritanopia.BoundarySix
        }
        ColorBlindMode.HIGH_CONTRAST_MONO -> when (colorType) {
            AccessibleColorType.PRIMARY -> ColorBlindPalette.HighContrastMono.Primary
            AccessibleColorType.SUCCESS -> ColorBlindPalette.HighContrastMono.Primary
            AccessibleColorType.ERROR -> ColorBlindPalette.HighContrastMono.Primary
            AccessibleColorType.WARNING -> ColorBlindPalette.HighContrastMono.Secondary
            AccessibleColorType.LIVE -> ColorBlindPalette.HighContrastMono.Accent
            AccessibleColorType.WICKET -> ColorBlindPalette.HighContrastMono.Primary
            AccessibleColorType.BOUNDARY -> ColorBlindPalette.HighContrastMono.Accent
        }
    }
}

enum class AccessibleColorType {
    PRIMARY,
    SUCCESS,
    ERROR,
    WARNING,
    LIVE,
    WICKET,
    BOUNDARY
}

// ==========================================
// High Contrast Border Modifier
// ==========================================

/**
 * High contrast border modifier for improved visibility
 */
fun Modifier.highContrastBorder(
    width: Dp = 2.dp,
    color: Color = Color.White,
    cornerRadius: Dp = 8.dp,
    isDashed: Boolean = false
): Modifier = if (isDashed) {
    drawBehind {
        try {
            val strokeWidth = width.toPx()
            val dashPath = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            drawRoundRect(
                color = color,
                cornerRadius = CornerRadius(cornerRadius.toPx()),
                style = Stroke(width = strokeWidth, pathEffect = dashPath)
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
} else {
    border(
        width = width,
        color = color,
        shape = RoundedCornerShape(cornerRadius)
    )
}

/**
 * Adaptive high contrast border that increases visibility based on accessibility settings
 */
@Composable
fun Modifier.adaptiveHighContrastBorder(
    enabled: Boolean = true,
    width: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    cornerRadius: Dp = 8.dp
): Modifier {
    return if (enabled) {
        this.highContrastBorder(width, color, cornerRadius)
    } else {
        this
    }
}

// ==========================================
// Text Scaling Utilities
// ==========================================

/**
 * Large text mode preferences
 */
object LargeTextMode {
    
    /**
     * Available text scale factors
     */
    enum class Scale(val factor: Float) {
        NORMAL(1.0f),
        LARGE(1.25f),
        EXTRA_LARGE(1.5f),
        HUGE(2.0f)
    }
    
    /**
     * Calculate scaled text size
     */
    fun scaledSize(baseSizeSp: Int, scale: Scale): Int {
        try {
            return (baseSizeSp * scale.factor).roundToInt()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Get scaled TextStyle
     */
    @Composable
    fun scaledTextStyle(
        baseStyle: TextStyle,
        scale: Scale
    ): TextStyle {
        val scaledFontSize = (baseStyle.fontSize.value * scale.factor).sp
        val scaledLineHeight = baseStyle.lineHeight.takeIf { it.isSp }?.let {
            (it.value * scale.factor).sp
        } ?: baseStyle.lineHeight
        
        return baseStyle.copy(
            fontSize = scaledFontSize,
            lineHeight = scaledLineHeight
        )
    }
}

/**
 * Modifier for text size adjustment based on accessibility preferences
 */
@Composable
fun Modifier.textScaling(
    scale: LargeTextMode.Scale
): Modifier = composed {
    val config = LocalConfiguration.current
    val fontScale = config.fontScale * scale.factor
    this
}

/**
 * Composable for providing scaled text consistently
 */
@Composable
fun scaledTextStyle(
    baseStyle: TextStyle = LocalTextStyle.current,
    accessibilityScale: LargeTextMode.Scale = LargeTextMode.Scale.NORMAL
): TextStyle {
    return LargeTextMode.scaledTextStyle(baseStyle, accessibilityScale)
}

// ==========================================
// Minimum Touch Target
// ==========================================

/**
 * Minimum touch target size (48dp as per accessibility guidelines)
 */
val MinimumTouchTargetSize = 48.dp

/**
 * Modifier ensuring minimum touch target of 48dp
 */
fun Modifier.minimumTouchTarget(
    minSize: Dp = MinimumTouchTargetSize
): Modifier = this.sizeIn(minWidth = minSize, minHeight = minSize)

/**
 * Modifier extension to expand touch target while keeping visual size
 */
fun Modifier.expandedTouchTarget(
    targetSize: Dp = MinimumTouchTargetSize
): Modifier = this
    .sizeIn(minWidth = targetSize, minHeight = targetSize)
    .wrapContentSize()

// ==========================================
// Focus Indicator
// ==========================================

/**
 * High visibility focus ring for keyboard/switch navigation
 */
@Composable
fun Modifier.focusIndicator(
    interactionSource: MutableInteractionSource,
    focusColor: Color = Color(0xFF0077BB),
    unfocusedColor: Color = Color.Transparent,
    borderWidth: Dp = 3.dp,
    cornerRadius: Dp = 8.dp,
    offset: Dp = 2.dp
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) focusColor else unfocusedColor
    
    return this
        .padding(offset)
        .border(
            width = if (isFocused) borderWidth else 0.dp,
            color = borderColor,
            shape = RoundedCornerShape(cornerRadius)
        )
}

/**
 * Alternative focus indicator with background highlight
 */
@Composable
fun Modifier.focusHighlight(
    interactionSource: MutableInteractionSource,
    focusColor: Color = Color(0xFF0077BB).copy(alpha = 0.3f),
    cornerRadius: Dp = 8.dp
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    return if (isFocused) {
        background(
            color = focusColor,
            shape = RoundedCornerShape(cornerRadius)
        )
    } else {
        this
    }
}

// ==========================================
// WCAG Contrast Checker
// ==========================================

/**
 * Utility object for checking WCAG color contrast compliance
 */
object ContrastChecker {
    
    /**
     * WCAG 2.1 contrast ratio levels
     */
    enum class Level(val minRatio: Float) {
        AA_NORMAL_TEXT(4.5f),
        AA_LARGE_TEXT(3.0f),
        AAA_NORMAL_TEXT(7.0f),
        AAA_LARGE_TEXT(4.5f),
        AA_UI_COMPONENTS(3.0f)
    }
    
    /**
     * Calculate relative luminance of a color
     */
    fun relativeLuminance(color: Color): Float {
        try {
            fun adjust(component: Float): Float {
                return if (component <= 0.03928f) {
                    component / 12.92f
                } else {
                    ((component + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
                }
            }
        
            val r = adjust(color.red)
            val g = adjust(color.green)
            val b = adjust(color.blue)
        
            return 0.2126f * r + 0.7152f * g + 0.0722f * b
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Calculate contrast ratio between two colors
     */
    fun contrastRatio(foreground: Color, background: Color): Float {
        try {
            val lumFg = relativeLuminance(foreground)
            val lumBg = relativeLuminance(background)
        
            val lighter = maxOf(lumFg, lumBg)
            val darker = minOf(lumFg, lumBg)
        
            return (lighter + 0.05f) / (darker + 0.05f)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Check if color combination meets a specific WCAG level
     */
    fun meetsLevel(foreground: Color, background: Color, level: Level): Boolean {
        try {
            return contrastRatio(foreground, background) >= level.minRatio
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Get the highest WCAG level met by a color combination
     */
    fun getHighestLevel(foreground: Color, background: Color): Level? {
        try {
            val ratio = contrastRatio(foreground, background)
            return when {
                ratio >= Level.AAA_NORMAL_TEXT.minRatio -> Level.AAA_NORMAL_TEXT
                ratio >= Level.AA_NORMAL_TEXT.minRatio -> Level.AA_NORMAL_TEXT
                ratio >= Level.AA_LARGE_TEXT.minRatio -> Level.AA_LARGE_TEXT
                ratio >= Level.AA_UI_COMPONENTS.minRatio -> Level.AA_UI_COMPONENTS
                else -> null
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Suggest a more accessible color if contrast is insufficient
     */
    fun suggestAccessibleColor(
        foreground: Color,
        background: Color,
        targetLevel: Level = Level.AA_NORMAL_TEXT
    ): Color {
        if (meetsLevel(foreground, background, targetLevel)) {
            try {
                return foreground
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
        
        val bgLuminance = relativeLuminance(background)
        
        // Determine if we need lighter or darker foreground
        return if (bgLuminance > 0.5f) {
            // Dark background needs light foreground
            adjustColorForContrast(foreground, background, targetLevel, lighten = false)
        } else {
            // Light background needs dark foreground
            adjustColorForContrast(foreground, background, targetLevel, lighten = true)
        }
    }
    
    private fun adjustColorForContrast(
        foreground: Color,
        background: Color,
        targetLevel: Level,
        lighten: Boolean
    ): Color {
        var adjusted = foreground
        val step = 0.05f
        val maxIterations = 20
        
        repeat(maxIterations) {
            if (meetsLevel(adjusted, background, targetLevel)) {
                return adjusted
            }
            
            adjusted = if (lighten) {
                Color(
                    red = minOf(1f, adjusted.red + step),
                    green = minOf(1f, adjusted.green + step),
                    blue = minOf(1f, adjusted.blue + step),
                    alpha = adjusted.alpha
                )
            } else {
                Color(
                    red = maxOf(0f, adjusted.red - step),
                    green = maxOf(0f, adjusted.green - step),
                    blue = maxOf(0f, adjusted.blue - step),
                    alpha = adjusted.alpha
                )
            }
        }
        
        // If we couldn't achieve contrast, return black or white
        return if (lighten) Color.White else Color.Black
    }
}

// ==========================================
// Visual Accessibility Preferences
// ==========================================

/**
 * Visual accessibility preferences data class
 */
data class VisualAccessibilityPreferences(
    val colorBlindMode: ColorBlindMode = ColorBlindMode.NONE,
    val highContrastEnabled: Boolean = false,
    val largeTextScale: LargeTextMode.Scale = LargeTextMode.Scale.NORMAL,
    val boldTextEnabled: Boolean = false,
    val increasedContrastBorders: Boolean = false
)

/**
 * Local composition for visual accessibility preferences
 */
val LocalVisualAccessibilityPreferences = compositionLocalOf { 
    VisualAccessibilityPreferences() 
}

/**
 * Provider for visual accessibility preferences
 */
@Composable
fun VisualAccessibilityProvider(
    preferences: VisualAccessibilityPreferences,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalVisualAccessibilityPreferences provides preferences
    ) {
        content()
    }
}

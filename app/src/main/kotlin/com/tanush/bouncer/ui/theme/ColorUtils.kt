package com.tanush.bouncer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow

/**
 * Color Utilities for Contrast and Accessibility
 * 
 * Provides utilities for ensuring WCAG AA compliance (4.5:1 contrast ratio)
 * and manipulating colors for optimal readability and visual hierarchy.
 * 
 * WCAG Standards:
 * - AA Level: 4.5:1 for normal text, 3:1 for large text
 * - AAA Level: 7:1 for normal text, 4.5:1 for large text
 */

// =============================================================================
// Contrast Utilities
// =============================================================================

/**
 * Calculate the relative luminance of a color.
 * 
 * Uses the WCAG formula:
 * L = 0.2126 * R + 0.7152 * G + 0.0722 * B
 * 
 * Where R, G, B are linearized color components.
 * 
 * @return Luminance value between 0.0 (darkest) and 1.0 (lightest)
 */
fun Color.relativeLuminance(): Float {
    // Linearize color components
    fun linearize(component: Float): Float {
        return if (component <= 0.03928f) {
            component / 12.92f
        } else {
            ((component + 0.055f) / 1.055f).pow(2.4f)
        }
    }
    
    val r = linearize(red)
    val g = linearize(green)
    val b = linearize(blue)
    
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/**
 * Calculate contrast ratio between two colors.
 * 
 * Formula: (L1 + 0.05) / (L2 + 0.05)
 * Where L1 is the lighter color's luminance and L2 is darker.
 * 
 * @param other Color to compare against
 * @return Contrast ratio between 1.0 (no contrast) and 21.0 (maximum)
 */
fun Color.contrastRatio(other: Color): Float {
    val lum1 = this.relativeLuminance()
    val lum2 = other.relativeLuminance()
    
    val lighter = maxOf(lum1, lum2)
    val darker = minOf(lum1, lum2)
    
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Determine the best contrasting text color (white or black).
 * 
 * Returns white for dark backgrounds, black for light backgrounds.
 * Uses luminance threshold of 0.5 (can be adjusted for preference).
 * 
 * @param threshold Luminance threshold (default 0.5)
 * @return Color.White or Color.Black
 */
fun Color.contrastingTextColor(): Color {
    val whiteContrast = Color.White.contrastRatio(this)
    val blackContrast = Color.Black.contrastRatio(this)
    return if (whiteContrast >= blackContrast) Color.White else Color.Black
}

/**
 * Check if this color has sufficient contrast with another color.
 * 
 * @param other Background color to check against
 * @param ratio Minimum contrast ratio (default 4.5 for WCAG AA)
 * @return true if contrast ratio meets or exceeds the requirement
 */
fun Color.hasMinimumContrast(other: Color, ratio: Float = 4.5f): Boolean {
    return this.contrastRatio(other) >= ratio
}

/**
 * Ensure this color has minimum contrast with a background.
 * Adjusts the color if needed to meet WCAG AA standards (4.5:1).
 * 
 * @param background Background color to ensure contrast against
 * @param minimumRatio Minimum contrast ratio (default 4.5 for WCAG AA)
 * @param maxIterations Maximum adjustment iterations (default 50)
 * @return Adjusted color with sufficient contrast
 */
fun Color.ensureContrast(
    background: Color,
    minimumRatio: Float = 4.5f,
    maxIterations: Int = 50
): Color {
    var adjustedColor = this
    var iterations = 0
    
    // Check if already has sufficient contrast
    if (adjustedColor.hasMinimumContrast(background, minimumRatio)) {
        return adjustedColor
    }
    
    // Determine if we need to lighten or darken
    val backgroundLuminance = background.relativeLuminance()
    val shouldLighten = adjustedColor.relativeLuminance() < backgroundLuminance
    
    // Adjust until we reach minimum contrast
    while (!adjustedColor.hasMinimumContrast(background, minimumRatio) 
           && iterations < maxIterations) {
        adjustedColor = if (shouldLighten) {
            adjustedColor.lighten(0.05f)
        } else {
            adjustedColor.darken(0.05f)
        }
        iterations++
    }
    
    return adjustedColor
}


/**
 * Raise this colour until it separates from [background], keeping its hue.
 *
 * [ensureContrast] lifts by lerping toward white, which reaches the ratio but bleaches the colour —
 * Bangladesh's #006A4E arrives as grey. Raising value in HSV holds hue and saturation, so a filled
 * shape still reads as the team's colour. Only when full brightness is still too dark does this fall
 * back to the bleaching path, which is the right trade at that point: an invisible segment is worse
 * than an off-shade one.
 *
 * Meant for blocks of colour, not text. Glyphs need the higher ratios [ensureContrast] defaults to.
 */
fun Color.liftForContrast(background: Color, minimumRatio: Float): Color {
    if (hasMinimumContrast(background, minimumRatio)) return this
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    var value = hsv[2]
    while (value < 1f) {
        value = (value + HSV_VALUE_STEP).coerceAtMost(1f)
        val lifted = Color.hsv(hsv[0], hsv[1], value)
        if (lifted.hasMinimumContrast(background, minimumRatio)) return lifted
    }
    return ensureContrast(background, minimumRatio)
}

private const val HSV_VALUE_STEP = 0.04f

/**
 * Ensure this color is readable as foreground text using WCAG contrast.
 * Returns white or black (or a contrast-adjusted fallback) against this color.
 */
fun Color.ensureContrast(minRatio: Float = 4.5f): Color {
    val preferred = contrastingTextColor()
    if (preferred.hasMinimumContrast(this, minRatio)) return preferred

    val alternate = if (preferred == Color.White) Color.Black else Color.White
    if (alternate.hasMinimumContrast(this, minRatio)) return alternate

    return preferred.ensureContrast(background = this, minimumRatio = minRatio)
}

// =============================================================================
// Color Manipulation Utilities
// =============================================================================

/**
 * Lighten a color by a given factor.
 * 
 * @param factor Amount to lighten (0.0 to 1.0). 0.1 = 10% lighter
 * @return Lightened color
 */
fun Color.lighten(factor: Float = 0.12f): Color {
    val clampedFactor = factor.coerceIn(0f, 1f)
    
    return Color(
        red = (red + (1f - red) * clampedFactor).coerceIn(0f, 1f),
        green = (green + (1f - green) * clampedFactor).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * clampedFactor).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Darken a color by a given factor.
 * 
 * @param factor Amount to darken (0.0 to 1.0). 0.1 = 10% darker
 * @return Darkened color
 */
fun Color.darken(factor: Float = 0.12f): Color {
    val clampedFactor = factor.coerceIn(0f, 1f)
    
    return Color(
        red = (red * (1f - clampedFactor)).coerceIn(0f, 1f),
        green = (green * (1f - clampedFactor)).coerceIn(0f, 1f),
        blue = (blue * (1f - clampedFactor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Adjust saturation of a color.
 * 
 * @param factor Saturation adjustment (-1.0 to 1.0).
 *               Negative = desaturate, Positive = saturate
 * @return Color with adjusted saturation
 */
fun Color.adjustSaturation(factor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    
    // Adjust saturation (hsv[1])
    hsv[1] = (hsv[1] + factor).coerceIn(0f, 1f)
    
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Convert color to argb Int for interop with Android APIs.
 */
fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}

/**
 * Blend two colors together.
 * 
 * @param other Color to blend with
 * @param ratio Blend ratio (0.0 = this color, 1.0 = other color)
 * @return Blended color
 */
fun Color.blend(other: Color, ratio: Float = 0.5f): Color {
    val clampedRatio = ratio.coerceIn(0f, 1f)
    
    return Color(
        red = red * (1f - clampedRatio) + other.red * clampedRatio,
        green = green * (1f - clampedRatio) + other.green * clampedRatio,
        blue = blue * (1f - clampedRatio) + other.blue * clampedRatio,
        alpha = alpha * (1f - clampedRatio) + other.alpha * clampedRatio
    )
}

/**
 * Create a dimmed version of the color (for night mode).
 * This is an alias to the existing dimmed() function.
 * 
 * @param dimFactor Dimming factor (0.0 to 1.0). Default 0.3 = 30% dimmer
 * @return Dimmed color
 */
fun Color.dimForNightMode(dimFactor: Float = 0.3f): Color {
    return this.darken(dimFactor)
}

/**
 * Create a muted/desaturated version of the color.
 * 
 * @param amount Amount to desaturate (0.0 to 1.0). Default 0.5
 * @return Muted color
 */
fun Color.muted(amount: Float = 0.5f): Color {
    val clampedAmount = amount.coerceIn(0f, 1f)
    
    // Blend with gray to desaturate
    val gray = Color(
        red = luminance(),
        green = luminance(),
        blue = luminance(),
        alpha = alpha
    )
    
    return this.blend(gray, clampedAmount)
}

/**
 * Create a vibrant/saturated version of the color.
 * 
 * @param amount Amount to increase saturation (0.0 to 1.0). Default 0.3
 * @return Vibrant color
 */
fun Color.vibrant(amount: Float = 0.3f): Color {
    return this.adjustSaturation(amount)
}

// =============================================================================
// Accessibility Helpers
// =============================================================================

/**
 * Check if color meets WCAG AA standard for normal text.
 * 
 * @param background Background color
 * @return true if contrast ratio >= 4.5:1
 */
fun Color.meetsWCAG_AA(background: Color): Boolean {
    return this.hasMinimumContrast(background, 4.5f)
}

/**
 * Check if color meets WCAG AAA standard for normal text.
 * 
 * @param background Background color
 * @return true if contrast ratio >= 7:1
 */
fun Color.meetsWCAG_AAA(background: Color): Boolean {
    return this.hasMinimumContrast(background, 7.0f)
}

/**
 * Check if color meets WCAG AA standard for large text.
 * 
 * @param background Background color
 * @return true if contrast ratio >= 3:1
 */
fun Color.meetsWCAG_AA_Large(background: Color): Boolean {
    return this.hasMinimumContrast(background, 3.0f)
}

/**
 * Get the best text color that meets WCAG AA standards.
 * Tries white first, then black, then adjusts if needed.
 * 
 * @param preferWhite Prefer white text if both meet standards
 * @return White, black, or adjusted color that meets WCAG AA
 */
fun Color.accessibleTextColor(preferWhite: Boolean = true): Color {
    val white = Color.White
    val black = Color.Black
    
    // Check if white or black already meet standards
    val whiteContrast = white.contrastRatio(this)
    val blackContrast = black.contrastRatio(this)
    
    return when {
        whiteContrast >= 4.5f && (preferWhite || blackContrast < 4.5f) -> white
        blackContrast >= 4.5f -> black
        // If neither works, ensure contrast with preferred color
        preferWhite -> white.ensureContrast(this)
        else -> black.ensureContrast(this)
    }
}

// =============================================================================
// Color Analysis
// =============================================================================

/**
 * Determine if a color is considered "dark".
 * 
 * @param threshold Luminance threshold (default 0.5)
 * @return true if color is dark
 */
fun Color.isDark(threshold: Float = 0.5f): Boolean {
    return this.relativeLuminance() < threshold
}

/**
 * Determine if a color is considered "light".
 * 
 * @param threshold Luminance threshold (default 0.5)
 * @return true if color is light
 */
fun Color.isLight(threshold: Float = 0.5f): Boolean {
    return !this.isDark(threshold)
}

/**
 * Get the perceived brightness of a color (0.0 to 1.0).
 * Uses weighted average that matches human perception.
 */
fun Color.perceivedBrightness(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}

// =============================================================================
// Team Color Specific Utilities
// =============================================================================

/**
 * Create a surface color from a team primary color.
 * Ensures the surface is dark enough for use as a background.
 * 
 * @return Dark surface color based on team color
 */
fun Color.toTeamSurface(): Color {
    return this
        .darken(0.7f)
        .adjustSaturation(-0.4f)
        .ensureContrast(TextPrimary, 4.5f)
}

/**
 * Create an overlay color from a team color.
 * Useful for semi-transparent overlays.
 * 
 * @param alpha Overlay alpha (default 0.15)
 * @return Team color with reduced opacity
 */
fun Color.toTeamOverlay(alpha: Float = 0.15f): Color {
    return this.copy(alpha = alpha.coerceIn(0f, 1f))
}

/**
 * Create a border color from a team primary color.
 * Ensures visibility on dark backgrounds.
 * 
 * @return Border color based on team color
 */
fun Color.toTeamBorder(): Color {
    return this.adjustSaturation(0.2f).copy(alpha = 0.5f)
}

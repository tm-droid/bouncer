package com.tanush.bouncer.ui.adaptive

import androidx.compose.animation.core.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerTypography
import kotlin.math.max
import kotlin.math.min

/**
 * Text Scaling Support for Bouncer Cricket App
 * 
 * Provides accessible text composables that properly handle
 * system font scaling, 200% text scale support, and layout reflow.
 */

// ==========================================
// Font Scale Detection
// ==========================================

/**
 * Current font scale levels for accessibility
 */
enum class FontScaleLevel {
    /** Default system font scale (1.0x) */
    NORMAL,
    /** Slightly larger (1.0x - 1.15x) */
    MEDIUM,
    /** Large font scale (1.15x - 1.35x) */
    LARGE,
    /** Extra large (1.35x - 1.6x) */
    EXTRA_LARGE,
    /** Huge - maximum accessibility (1.6x - 2.0x+) */
    HUGE
}

/**
 * Information about current text scaling state
 */
data class TextScaleInfo(
    val fontScale: Float,
    val scaleLevel: FontScaleLevel,
    val isLargeText: Boolean,
    val density: Density
) {
    /** Whether layout should reflow to accommodate large text */
    val shouldReflow: Boolean get() = fontScale >= 1.35f
    
    /** Whether to use abbreviated text to save space */
    val useAbbreviatedText: Boolean get() = fontScale >= 1.6f
    
    /** Whether icons should be scaled with text */
    val shouldScaleIcons: Boolean get() = fontScale > 1.0f
}

/**
 * Remember the current text scale information
 */
@Composable
fun rememberTextScaleInfo(): TextScaleInfo {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    
    val scaleLevel = when {
        fontScale <= 1.0f -> FontScaleLevel.NORMAL
        fontScale <= 1.15f -> FontScaleLevel.MEDIUM
        fontScale <= 1.35f -> FontScaleLevel.LARGE
        fontScale <= 1.6f -> FontScaleLevel.EXTRA_LARGE
        else -> FontScaleLevel.HUGE
    }
    
    return remember(fontScale) {
        TextScaleInfo(
            fontScale = fontScale,
            scaleLevel = scaleLevel,
            isLargeText = fontScale > 1.0f,
            density = density
        )
    }
}

/**
 * CompositionLocal for accessing text scale info throughout the tree
 */
val LocalTextScaleInfo = staticCompositionLocalOf { 
    TextScaleInfo(
        fontScale = 1.0f,
        scaleLevel = FontScaleLevel.NORMAL,
        isLargeText = false,
        density = Density(1f)
    )
}

/**
 * Provider that exposes text scale info to child composables
 */
@Composable
fun TextScaleProvider(
    content: @Composable () -> Unit
) {
    val textScaleInfo = rememberTextScaleInfo()
    
    CompositionLocalProvider(
        LocalTextScaleInfo provides textScaleInfo
    ) {
        content()
    }
}

// ==========================================
// Accessible Text Composable
// ==========================================

/**
 * Strategy for handling text that doesn't fit in available space
 */
enum class TruncationStrategy {
    /** Use ellipsis (...) for overflow */
    ELLIPSIS,
    /** Scroll text horizontally (marquee effect) */
    MARQUEE,
    /** Wrap text to multiple lines */
    WRAP,
    /** Shrink text to fit */
    SHRINK,
    /** Show abbreviated version if available */
    ABBREVIATE
}

/**
 * Text composable that properly handles accessibility scaling
 * 
 * @param text The text to display
 * @param abbreviatedText Optional shortened text for extreme font scales
 * @param modifier Modifier for customization
 * @param style Base text style (will be scaled appropriately)
 * @param color Text color
 * @param truncationStrategy How to handle overflow when text doesn't fit
 * @param maxLines Maximum number of lines (for WRAP strategy)
 * @param minFontSize Minimum font size when using SHRINK strategy
 */
@Composable
fun AccessibleText(
    text: String,
    modifier: Modifier = Modifier,
    abbreviatedText: String? = null,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    truncationStrategy: TruncationStrategy = TruncationStrategy.ELLIPSIS,
    maxLines: Int = 1,
    minFontSize: TextUnit = 10.sp,
    textAlign: TextAlign? = null
) {
    val textScaleInfo = LocalTextScaleInfo.current
    
    // Use abbreviated text at extreme font scales if available
    val displayText = if (textScaleInfo.useAbbreviatedText && abbreviatedText != null) {
        abbreviatedText
    } else {
        text
    }
    
    when (truncationStrategy) {
        TruncationStrategy.ELLIPSIS -> {
            Text(
                text = displayText,
                modifier = modifier,
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign
            )
        }
        
        TruncationStrategy.MARQUEE -> {
            MarqueeText(
                text = displayText,
                modifier = modifier,
                style = style,
                color = color
            )
        }
        
        TruncationStrategy.WRAP -> {
            Text(
                text = displayText,
                modifier = modifier,
                style = style,
                color = color,
                maxLines = if (textScaleInfo.shouldReflow) Int.MAX_VALUE else maxLines,
                overflow = TextOverflow.Visible,
                textAlign = textAlign
            )
        }
        
        TruncationStrategy.SHRINK -> {
            AutoSizeText(
                text = displayText,
                modifier = modifier,
                style = style,
                color = color,
                maxLines = maxLines,
                minFontSize = minFontSize,
                textAlign = textAlign
            )
        }
        
        TruncationStrategy.ABBREVIATE -> {
            Text(
                text = abbreviatedText ?: displayText,
                modifier = modifier,
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign
            )
        }
    }
}

/**
 * Auto-sizing text that shrinks to fit available space
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    minFontSize: TextUnit = 10.sp,
    textAlign: TextAlign? = null
) {
    var scaledStyle by remember { mutableStateOf(style) }
    var readyToDraw by remember { mutableStateOf(false) }
    
    Text(
        text = text,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        style = scaledStyle,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        softWrap = false,
        onTextLayout = { result ->
            if (result.didOverflowWidth && scaledStyle.fontSize > minFontSize) {
                // Reduce font size by 5% and try again
                val newSize = (scaledStyle.fontSize.value * 0.95f).sp
                if (newSize >= minFontSize) {
                    scaledStyle = scaledStyle.copy(fontSize = newSize)
                } else {
                    scaledStyle = scaledStyle.copy(fontSize = minFontSize)
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        }
    )
}

/**
 * Marquee-style scrolling text for content that doesn't fit
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    scrollDuration: Int = 5000
) {
    val scrollState = rememberScrollState()
    var shouldAnimate by remember { mutableStateOf(false) }
    
    LaunchedEffect(shouldAnimate) {
        if (shouldAnimate) {
            while (true) {
                scrollState.animateScrollTo(
                    scrollState.maxValue,
                    animationSpec = tween(scrollDuration, easing = LinearEasing)
                )
                kotlinx.coroutines.delay(1000)
                scrollState.animateScrollTo(
                    0,
                    animationSpec = tween(scrollDuration, easing = LinearEasing)
                )
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    
    Row(
        modifier = modifier.horizontalScroll(scrollState, enabled = false)
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { result ->
                shouldAnimate = result.didOverflowWidth
            }
        )
    }
}

// ==========================================
// Text Reflow Layout
// ==========================================

/**
 * Layout that reflows content based on text scale
 * At normal text sizes: horizontal layout
 * At large text sizes: vertical layout
 */
@Composable
fun TextReflowLayout(
    modifier: Modifier = Modifier,
    textScaleInfo: TextScaleInfo = LocalTextScaleInfo.current,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable () -> Unit
) {
    if (textScaleInfo.shouldReflow) {
        Column(
            modifier = modifier,
            verticalArrangement = verticalArrangement
        ) {
            content()
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

/**
 * Layout for label-value pairs that reflows at large text sizes
 * Normal: "Label: Value" on one line
 * Large text: Label on first line, Value on second line
 */
@Composable
fun LabelValueReflow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    textScaleInfo: TextScaleInfo = LocalTextScaleInfo.current,
    labelStyle: TextStyle = MaterialTheme.typography.bodySmall,
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    if (textScaleInfo.shouldReflow) {
        Column(modifier = modifier) {
            Text(
                text = label,
                style = labelStyle,
                color = labelColor
            )
            Text(
                text = value,
                style = valueStyle,
                color = valueColor
            )
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label:",
                style = labelStyle,
                color = labelColor
            )
            Text(
                text = value,
                style = valueStyle,
                color = valueColor
            )
        }
    }
}

// ==========================================
// Icon-Text Scaling
// ==========================================

/**
 * Size specifications for icons that scale with text
 */
data class ScaledIconSize(
    val sizeDp: Dp,
    val paddingDp: Dp
)

/**
 * Calculate icon size that scales appropriately with text size
 */
@Composable
fun rememberScaledIconSize(
    baseSize: Dp = 24.dp,
    basePadding: Dp = 4.dp,
    textScaleInfo: TextScaleInfo = LocalTextScaleInfo.current,
    minSize: Dp = 16.dp,
    maxSize: Dp = 48.dp
): ScaledIconSize {
    return remember(textScaleInfo.fontScale, baseSize) {
        val scaledSize = (baseSize.value * textScaleInfo.fontScale).dp
        val clampedSize = maxOf(minSize, minOf(maxSize, scaledSize))
        val scaledPadding = (basePadding.value * textScaleInfo.fontScale).dp
        
        ScaledIconSize(
            sizeDp = clampedSize,
            paddingDp = scaledPadding
        )
    }
}

/**
 * Composable for icon-text pairs that scale together
 */
@Composable
fun IconTextScaling(
    icon: @Composable (size: Dp) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    textScaleInfo: TextScaleInfo = LocalTextScaleInfo.current,
    baseIconSize: Dp = 24.dp,
    spacing: Dp = 8.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = Color.Unspecified,
    arrangement: Arrangement.Horizontal = Arrangement.Start
) {
    val iconSize = rememberScaledIconSize(
        baseSize = baseIconSize,
        textScaleInfo = textScaleInfo
    )
    
    if (textScaleInfo.shouldReflow && textScaleInfo.scaleLevel == FontScaleLevel.HUGE) {
        // Stack vertically at extreme scales
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            icon(iconSize.sizeDp)
            Text(
                text = text,
                style = textStyle,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = arrangement,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon(iconSize.sizeDp)
            Spacer(modifier = Modifier.width(spacing))
            Text(
                text = text,
                style = textStyle,
                color = textColor
            )
        }
    }
}

// ==========================================
// Adaptive Typography
// ==========================================

/**
 * Typography scale that adapts to display density and size
 */
object AdaptiveTypography {
    
    /**
     * Get scaled text style based on display density
     */
    @Composable
    fun scaledStyle(
        baseStyle: TextStyle,
        displayDensity: DisplayDensity = rememberDisplayDensity()
    ): TextStyle {
        val fontSizeMultiplier = when (displayDensity) {
            DisplayDensity.LOW -> 1.1f  // Slightly larger on low-density displays
            DisplayDensity.MEDIUM -> 1.0f
            DisplayDensity.HIGH -> 0.95f  // Slightly smaller on high-density
            DisplayDensity.EXTRA_HIGH -> 0.9f
        }
        
        return baseStyle.copy(
            fontSize = (baseStyle.fontSize.value * fontSizeMultiplier).sp,
            lineHeight = (baseStyle.lineHeight.value * fontSizeMultiplier).sp
        )
    }
    
    /**
     * Display headline with density adaptation
     */
    @Composable
    fun displayLarge(): TextStyle = scaledStyle(MaterialTheme.typography.displayLarge)
    
    @Composable
    fun displayMedium(): TextStyle = scaledStyle(MaterialTheme.typography.displayMedium)
    
    @Composable
    fun displaySmall(): TextStyle = scaledStyle(MaterialTheme.typography.displaySmall)
    
    @Composable
    fun headlineLarge(): TextStyle = scaledStyle(MaterialTheme.typography.headlineLarge)
    
    @Composable
    fun headlineMedium(): TextStyle = scaledStyle(MaterialTheme.typography.headlineMedium)
    
    @Composable
    fun headlineSmall(): TextStyle = scaledStyle(MaterialTheme.typography.headlineSmall)
    
    @Composable
    fun titleLarge(): TextStyle = scaledStyle(MaterialTheme.typography.titleLarge)
    
    @Composable
    fun titleMedium(): TextStyle = scaledStyle(MaterialTheme.typography.titleMedium)
    
    @Composable
    fun titleSmall(): TextStyle = scaledStyle(MaterialTheme.typography.titleSmall)
    
    @Composable
    fun bodyLarge(): TextStyle = scaledStyle(MaterialTheme.typography.bodyLarge)
    
    @Composable
    fun bodyMedium(): TextStyle = scaledStyle(MaterialTheme.typography.bodyMedium)
    
    @Composable
    fun bodySmall(): TextStyle = scaledStyle(MaterialTheme.typography.bodySmall)
    
    @Composable
    fun labelLarge(): TextStyle = scaledStyle(MaterialTheme.typography.labelLarge)
    
    @Composable
    fun labelMedium(): TextStyle = scaledStyle(MaterialTheme.typography.labelMedium)
    
    @Composable
    fun labelSmall(): TextStyle = scaledStyle(MaterialTheme.typography.labelSmall)
}

enum class DisplayDensity {
    LOW,      // < 160 dpi
    MEDIUM,   // 160-240 dpi
    HIGH,     // 240-320 dpi
    EXTRA_HIGH // > 320 dpi
}

@Composable
fun rememberDisplayDensity(): DisplayDensity {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val dpi = density.density * 160
    
    return remember(dpi) {
        when {
            dpi < 160 -> DisplayDensity.LOW
            dpi < 240 -> DisplayDensity.MEDIUM
            dpi < 320 -> DisplayDensity.HIGH
            else -> DisplayDensity.EXTRA_HIGH
        }
    }
}

// ==========================================
// Large Text Layout Adjustments
// ==========================================

/**
 * Provides layout adjustments for large text accessibility settings
 */
object LargeTextLayoutAdjustments {
    
    /**
     * Calculate adjusted padding that increases with font scale
     * Ensures touch targets remain accessible at large text sizes
     */
    @Composable
    fun adjustedPadding(
        basePadding: Dp,
        textScaleInfo: TextScaleInfo = LocalTextScaleInfo.current
    ): Dp {
        val multiplier = when (textScaleInfo.scaleLevel) {
            FontScaleLevel.NORMAL -> 1.0f
            FontScaleLevel.MEDIUM -> 1.1f
            FontScaleLevel.LARGE -> 1.2f
            FontScaleLevel.EXTRA_LARGE -> 1.3f
            FontScaleLevel.HUGE -> 1.4f
        }
        return (basePadding.value * multiplier).dp
    }
    
    /**
     * Calculate minimum touch target size based on font scale
     * Material guidelines: 48dp minimum, scales up with accessibility settings
     */
    @Composable
    fun minTouchTarget(
        textScaleInfo: TextScaleInfo = LocalTextScaleInfo.current
    ): Dp {
        val baseSize = 48.dp
        val multiplier = when (textScaleInfo.scaleLevel) {
            FontScaleLevel.NORMAL -> 1.0f
            FontScaleLevel.MEDIUM -> 1.0f
            FontScaleLevel.LARGE -> 1.1f
            FontScaleLevel.EXTRA_LARGE -> 1.2f
            FontScaleLevel.HUGE -> 1.3f
        }
        return (baseSize.value * multiplier).dp
    }
    
    /**
     * Calculate card height that accommodates large text
     */
    @Composable
    fun cardMinHeight(
        baseHeight: Dp,
        textScaleInfo: TextScaleInfo = LocalTextScaleInfo.current
    ): Dp {
        val multiplier = max(1.0f, textScaleInfo.fontScale)
        return (baseHeight.value * multiplier).dp
    }
    
    /**
     * Get number of columns for grid layouts at large text sizes
     * Reduces columns to give text more space
     */
    @Composable
    fun gridColumns(
        baseColumns: Int,
        textScaleInfo: TextScaleInfo = LocalTextScaleInfo.current
    ): Int {
        return when (textScaleInfo.scaleLevel) {
            FontScaleLevel.NORMAL, FontScaleLevel.MEDIUM -> baseColumns
            FontScaleLevel.LARGE -> max(1, baseColumns - 1)
            FontScaleLevel.EXTRA_LARGE, FontScaleLevel.HUGE -> max(1, baseColumns - 2)
        }
    }
    
    /**
     * Calculate spacing between items that increases with font scale
     */
    @Composable
    fun itemSpacing(
        baseSpacing: Dp,
        textScaleInfo: TextScaleInfo = LocalTextScaleInfo.current
    ): Dp {
        val multiplier = when (textScaleInfo.scaleLevel) {
            FontScaleLevel.NORMAL -> 1.0f
            FontScaleLevel.MEDIUM -> 1.1f
            FontScaleLevel.LARGE -> 1.2f
            FontScaleLevel.EXTRA_LARGE -> 1.4f
            FontScaleLevel.HUGE -> 1.6f
        }
        return (baseSpacing.value * multiplier).dp
    }
}

/**
 * Modifier extension for applying large text adjustments
 */
@Composable
fun Modifier.largeTextAware(
    textScaleInfo: TextScaleInfo = LocalTextScaleInfo.current,
    baseHorizontalPadding: Dp = 16.dp,
    baseVerticalPadding: Dp = 12.dp
): Modifier {
    val horizontalPadding = LargeTextLayoutAdjustments.adjustedPadding(
        baseHorizontalPadding,
        textScaleInfo
    )
    val verticalPadding = LargeTextLayoutAdjustments.adjustedPadding(
        baseVerticalPadding,
        textScaleInfo
    )
    
    return this
        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        .heightIn(min = LargeTextLayoutAdjustments.minTouchTarget(textScaleInfo))
}

// ==========================================
// Cricket-Specific Text Components
// ==========================================

/**
 * Score display that handles large text properly
 */
@Composable
fun AccessibleScoreText(
    runs: Int,
    wickets: Int,
    overs: String,
    modifier: Modifier = Modifier,
    textScaleInfo: TextScaleInfo = LocalTextScaleInfo.current,
    scoreStyle: TextStyle = MaterialTheme.typography.headlineLarge,
    oversStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    scoreColor: Color = MaterialTheme.colorScheme.onSurface,
    oversColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val scoreText = "$runs/$wickets"
    
    if (textScaleInfo.shouldReflow) {
        // Stack vertically at large text sizes
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AutoSizeText(
                text = scoreText,
                style = scoreStyle.copy(fontWeight = FontWeight.Bold),
                color = scoreColor,
                maxLines = 1
            )
            Text(
                text = "($overs ov)",
                style = oversStyle,
                color = oversColor
            )
        }
    } else {
        // Horizontal layout at normal text sizes
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = scoreText,
                style = scoreStyle.copy(fontWeight = FontWeight.Bold),
                color = scoreColor
            )
            Text(
                text = "($overs ov)",
                style = oversStyle,
                color = oversColor
            )
        }
    }
}

/**
 * Team name display with abbreviation support for large text
 */
@Composable
fun AccessibleTeamName(
    fullName: String,
    abbreviation: String,
    modifier: Modifier = Modifier,
    textScaleInfo: TextScaleInfo = LocalTextScaleInfo.current,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    AccessibleText(
        text = fullName,
        abbreviatedText = abbreviation,
        modifier = modifier,
        style = style,
        color = color,
        truncationStrategy = if (textScaleInfo.useAbbreviatedText) {
            TruncationStrategy.ABBREVIATE
        } else {
            TruncationStrategy.ELLIPSIS
        },
        maxLines = 1
    )
}

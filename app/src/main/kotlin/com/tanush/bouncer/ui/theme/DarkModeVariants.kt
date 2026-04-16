package com.tanush.bouncer.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.Build
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.sentry.Sentry

// ==========================================
// BOUNCER - Dark Mode Variants System (v1.0)
// OLED, Dimmed, and Custom Dark Themes
// ==========================================

/**
 * Dark mode variant types
 */
enum class DarkModeVariant(
    val label: String,
    val description: String
) {
    Standard(
        label = "Standard Dark",
        description = "Default dark theme"
    ),
    OLED(
        label = "OLED Black",
        description = "Pure black for OLED displays"
    ),
    Dimmed(
        label = "Dimmed",
        description = "Reduced brightness for night viewing"
    ),
    Dynamic(
        label = "Dynamic",
        description = "Adapts to your wallpaper colors"
    )
}

/**
 * OLED Black color scheme with pure black backgrounds
 */
object OLEDBlackScheme {
    val background = Color.Black
    val surface = Color(0xFF050505)
    val surfaceVariant = Color(0xFF0A0A0A)
    val onBackground = Color.White
    val onSurface = Color.White
    val primary = GoldDark
    val secondary = GoldPrimary
    val outline = Color(0xFF1A1A1A)
    
    val colorScheme = darkColorScheme(
        primary = primary,
        onPrimary = Color.Black,
        primaryContainer = primary.copy(alpha = 0.2f),
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = Color.Black,
        secondaryContainer = secondary.copy(alpha = 0.2f),
        onSecondaryContainer = secondary,
        tertiary = ChartCyan,
        onTertiary = Color.Black,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = TextSecondary,
        surfaceTint = primary,
        surfaceBright = Color(0xFF0F0F0F),
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = Color(0xFF121212),
        surfaceContainerLow = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceDim = Color.Black,
        inverseSurface = Color.White,
        inverseOnSurface = Color.Black,
        inversePrimary = ShinyGoldDark,
        outline = outline,
        outlineVariant = Color(0xFF0F0F0F),
        scrim = Color.Black.copy(alpha = 0.9f),
        error = ErrorRed,
        onError = Color.White,
        errorContainer = ErrorRedMuted,
        onErrorContainer = ErrorRed
    )
}

/**
 * Dimmed mode color scheme for night viewing
 */
object DimmedModeScheme {
    private const val DIM_FACTOR = 0.7f
    
    val background = BlackBackground.dimmed(DIM_FACTOR)
    val surface = BlackSurface.dimmed(DIM_FACTOR)
    val surfaceVariant = BlackElevated.dimmed(DIM_FACTOR)
    val onBackground = TextPrimary.dimmed(0.85f)
    val onSurface = TextPrimary.dimmed(0.85f)
    val primary = ShinyGoldDeep.dimmed(0.8f)
    val secondary = ShinyGoldMid.dimmed(0.8f)
    
    val colorScheme = darkColorScheme(
        primary = primary,
        onPrimary = Color.Black.dimmed(DIM_FACTOR),
        primaryContainer = primary.copy(alpha = 0.25f),
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = Color.Black.dimmed(DIM_FACTOR),
        secondaryContainer = secondary.copy(alpha = 0.25f),
        onSecondaryContainer = secondary,
        tertiary = ChartCyan.dimmed(0.8f),
        onTertiary = Color.Black.dimmed(DIM_FACTOR),
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = TextSecondary.dimmed(0.85f),
        surfaceTint = primary,
        surfaceBright = Color(0xFF1A1A1A).dimmed(DIM_FACTOR),
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = Color(0xFF1A1A1A).dimmed(DIM_FACTOR),
        surfaceContainerLow = Color(0xFF080808).dimmed(DIM_FACTOR),
        surfaceContainerLowest = Color.Black,
        surfaceDim = Color(0xFF050505),
        inverseSurface = Color.White.dimmed(0.9f),
        inverseOnSurface = Color.Black,
        inversePrimary = ShinyGoldDark.dimmed(0.8f),
        outline = GlassBorder.dimmed(0.8f),
        outlineVariant = GlassBorderLight.dimmed(0.8f),
        scrim = Color.Black.copy(alpha = 0.8f),
        error = ErrorRed.dimmed(0.85f),
        onError = Color.White.dimmed(0.9f),
        errorContainer = ErrorRedMuted.dimmed(DIM_FACTOR),
        onErrorContainer = ErrorRed.dimmed(0.85f)
    )
    
    private fun Color.dimmed(factor: Float): Color {
        try {
            return Color(
                red = this.red * factor,
                green = this.green * factor,
                blue = this.blue * factor,
                alpha = this.alpha
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

/**
 * Extension function to dim a color
 */
fun Color.dimmed(factor: Float): Color {
    try {
        return Color(
            red = this.red * factor,
            green = this.green * factor,
            blue = this.blue * factor,
            alpha = this.alpha
        )
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

val LocalDarkModeVariant = staticCompositionLocalOf { DarkModeVariant.Standard }

/**
 * Smooth theme transition with animated crossfade
 */
@Composable
fun SmoothThemeTransition(
    variant: DarkModeVariant,
    durationMillis: Int = AnimationDuration.moderate,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when (variant) {
        DarkModeVariant.Standard -> MaterialTheme.colorScheme
        DarkModeVariant.OLED -> OLEDBlackScheme.colorScheme
        DarkModeVariant.Dimmed -> DimmedModeScheme.colorScheme
        DarkModeVariant.Dynamic -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blendWithGoldAccents(dynamicDarkColorScheme(context))
        } else {
            MaterialTheme.colorScheme
        }
    }
    
    // Animate primary colors for smooth transition
    val animatedPrimary by animateColorAsState(
        targetValue = colorScheme.primary,
        animationSpec = tween(durationMillis),
        label = "primaryTransition"
    )
    
    val animatedBackground by animateColorAsState(
        targetValue = colorScheme.background,
        animationSpec = tween(durationMillis),
        label = "backgroundTransition"
    )
    
    val animatedSurface by animateColorAsState(
        targetValue = colorScheme.surface,
        animationSpec = tween(durationMillis),
        label = "surfaceTransition"
    )
    
    val animatedOnBackground by animateColorAsState(
        targetValue = colorScheme.onBackground,
        animationSpec = tween(durationMillis),
        label = "onBackgroundTransition"
    )
    
    val transitionedColorScheme = colorScheme.copy(
        primary = animatedPrimary,
        background = animatedBackground,
        surface = animatedSurface,
        onBackground = animatedOnBackground
    )
    
    CompositionLocalProvider(
        LocalDarkModeVariant provides variant
    ) {
        MaterialTheme(
            colorScheme = transitionedColorScheme,
            typography = BouncerMaterial3Typography,
            content = content
        )
    }
}

/**
 * Theme selector composable with visual preview cards
 */
@Composable
fun ThemeSelector(
    selectedVariant: DarkModeVariant,
    onVariantSelected: (DarkModeVariant) -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = BouncerDesignSystem.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Dark Mode Theme",
            style = typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            DarkModeVariant.entries.forEach { variant ->
                ThemePreviewCard(
                    variant = variant,
                    isSelected = variant == selectedVariant,
                    onClick = { onVariantSelected(variant) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Individual theme preview card
 */
@Composable
private fun ThemePreviewCard(
    variant: DarkModeVariant,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = BouncerDesignSystem.typography
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) ShinyGoldDeep else GlassBorder,
        animationSpec = tween(200),
        label = "themeBorder"
    )
    
    val (bgColor, surfaceColor, textColor) = when (variant) {
        DarkModeVariant.Standard -> Triple(BlackBackground, BlackSurface, TextPrimary)
        DarkModeVariant.OLED -> Triple(Color.Black, Color(0xFF050505), Color.White)
        DarkModeVariant.Dimmed -> Triple(
            BlackBackground.dimmed(0.7f),
            BlackSurface.dimmed(0.7f),
            TextPrimary.dimmed(0.85f)
        )
        DarkModeVariant.Dynamic -> Triple(
            Color(0xFF1A1A2E),   // Wallpaper-tinted preview hint
            Color(0xFF16213E),
            TextPrimary
        )
    }
    
    Card(
        modifier = modifier
            .aspectRatio(0.8f)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = bgColor
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Mini preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(surfaceColor)
                    .padding(8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Mock content lines
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(textColor.copy(alpha = 0.6f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(textColor.copy(alpha = 0.4f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ShinyGoldDeep.copy(alpha = if (variant == DarkModeVariant.Dimmed) 0.6f else 0.8f))
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = variant.label,
                style = typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isSelected) ShinyGoldDeep else textColor
            )
            
            Text(
                text = variant.description,
                style = typography.labelSmall,
                color = textColor.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
    }
}

/**
 * Auto theme switch based on system dark mode setting
 */
@Composable
fun AutoThemeSwitch(
    lightVariant: DarkModeVariant = DarkModeVariant.Standard,
    darkVariant: DarkModeVariant = DarkModeVariant.Standard,
    oledInDarkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    
    val selectedVariant = when {
        isSystemDark && oledInDarkMode -> DarkModeVariant.OLED
        isSystemDark -> darkVariant
        else -> lightVariant
    }
    
    SmoothThemeTransition(
        variant = selectedVariant,
        content = content
    )
}

/**
 * Per-component brightness modifier for adjusting individual element brightness
 */
fun Modifier.perComponentBrightness(
    brightness: Float = 1f
): Modifier = this
    .graphicsLayer {
        try {
            alpha = brightness.coerceIn(0f, 1f)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

/**
 * Enhanced brightness modifier with color adjustment
 */
@Composable
fun Modifier.adjustedBrightness(
    factor: Float = 1f,
    respectDarkMode: Boolean = true
): Modifier {
    val variant = LocalDarkModeVariant.current
    
    val effectiveFactor = when {
        !respectDarkMode -> factor
        variant == DarkModeVariant.Dimmed -> factor * 0.7f
        else -> factor
    }
    
    return this.alpha(effectiveFactor.coerceIn(0f, 1f))
}

/**
 * Dimmed overlay modifier for night viewing
 */
@Composable
fun Modifier.dimmedOverlay(
    enabled: Boolean = true,
    dimAmount: Float = 0.3f
): Modifier {
    if (!enabled) return this
    
    return this.drawBehind {
        drawRect(
            color = Color.Black.copy(alpha = dimAmount)
        )
    }
}

/**
 * Night mode wrapper that applies dimming and color adjustments
 */
@Composable
fun NightModeWrapper(
    enabled: Boolean,
    dimAmount: Float = 0.3f,
    content: @Composable () -> Unit
) {
    if (enabled) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dimmedOverlay(enabled = true, dimAmount = dimAmount)
        ) {
            content()
        }
    } else {
        content()
    }
}

/**
 * Brightness adjustment bar for manual control
 */
@Composable
fun BrightnessAdjustmentBar(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = BouncerDesignSystem.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Brightness",
                style = typography.titleMedium,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "${(brightness * 100).toInt()}%",
                style = typography.labelMedium,
                color = ShinyGoldDeep
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Custom brightness slider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BlackElevated)
        ) {
            // Track fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(brightness)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                ShinyGoldDim,
                                ShinyGoldDeep
                            )
                        )
                    )
            )
            
            // Thumb
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = ((brightness * 100) - 2).coerceAtLeast(0f).dp.times(2.8f))
                        .size(36.dp)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, ShinyGoldDeep, CircleShape)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Dim",
                style = typography.labelSmall,
                color = TextTertiary
            )
            Text(
                text = "Bright",
                style = typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

/**
 * Dark mode variant accessor object
 */
object DarkModeVariants {

    val current: DarkModeVariant
        @Composable
        get() = LocalDarkModeVariant.current

    val isOLED: Boolean
        @Composable
        get() = LocalDarkModeVariant.current == DarkModeVariant.OLED

    val isDimmed: Boolean
        @Composable
        get() = LocalDarkModeVariant.current == DarkModeVariant.Dimmed

    val isStandard: Boolean
        @Composable
        get() = LocalDarkModeVariant.current == DarkModeVariant.Standard

    /** `true` when the Dynamic wallpaper-adaptive variant is active. */
    val isDynamic: Boolean
        @Composable
        get() = LocalDarkModeVariant.current == DarkModeVariant.Dynamic

    /** Whether the current device supports Material You dynamic colors (Android 12+). */
    val supportsDynamicColor: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

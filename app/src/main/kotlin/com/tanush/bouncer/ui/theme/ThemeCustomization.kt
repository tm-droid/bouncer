package com.tanush.bouncer.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import io.sentry.Sentry

// ==========================================
// BOUNCER - Theme Customization System (v1.0)
// User Preferences & Visual Customization Tools
// ==========================================

/**
 * Card density options for list items and cards
 */
enum class CardDensity(
    val verticalPadding: Dp,
    val horizontalPadding: Dp,
    val spacing: Dp,
    val label: String
) {
    Compact(8.dp, 12.dp, 8.dp, "Compact"),
    Comfortable(16.dp, 16.dp, 12.dp, "Comfortable"),
    Spacious(24.dp, 20.dp, 16.dp, "Spacious")
}

/**
 * Font scaling options
 */
enum class FontScaling(
    val scale: Float,
    val label: String,
    val description: String
) {
    Small(0.85f, "Small", "Smaller text for more content"),
    Medium(1.0f, "Medium", "Default text size"),
    Large(1.15f, "Large", "Larger text for readability"),
    ExtraLarge(1.35f, "Extra Large", "Maximum text size")
}

/**
 * Animation intensity levels
 */
enum class AnimationIntensity(
    val scale: Float,
    val durationMultiplier: Float,
    val label: String
) {
    None(1f, 0f, "None"),
    Subtle(0.5f, 0.7f, "Subtle"),
    Normal(1f, 1f, "Normal"),
    Expressive(1.3f, 1.2f, "Expressive")
}

private inline fun <T> reportingFailures(block: () -> T): T {
    try {
        return block()
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/**
 * App preferences data class containing all customization options
 */
@Immutable
data class AppPreferences(
    val density: CardDensity = CardDensity.Comfortable,
    val fontSize: FontScaling = FontScaling.Medium,
    val accentColor: Color = GoldDark,
    val animationIntensity: AnimationIntensity = AnimationIntensity.Normal,
    val reducedMotion: Boolean = false
) {
    /**
     * Get scaled font size based on preferences
     */
    fun scaleFontSize(baseSize: TextUnit): TextUnit = reportingFailures {
        (baseSize.value * fontSize.scale).sp
    }

    /**
     * Get animation duration based on preferences
     */
    fun getAnimationDuration(baseDuration: Int): Int = reportingFailures {
        if (reducedMotion) 0 else (baseDuration * animationIntensity.durationMultiplier).toInt()
    }

    /**
     * Check if animations should be shown
     */
    fun shouldAnimate(): Boolean = reportingFailures {
        !reducedMotion && animationIntensity != AnimationIntensity.None
    }
}

val LocalAppPreferences = staticCompositionLocalOf { AppPreferences() }

/**
 * Font scaling utilities
 */
object FontScalingUtils {

    /**
     * Apply font scaling to a text unit
     */
    fun TextUnit.scaled(scaling: FontScaling): TextUnit = reportingFailures {
        (this.value * scaling.scale).sp
    }

    /**
     * Get the scaled typography values
     */
    @Composable
    fun getScaledTextStyle(
        baseSize: TextUnit,
        fontWeight: FontWeight = FontWeight.Normal
    ): androidx.compose.ui.text.TextStyle {
        val preferences = LocalAppPreferences.current
        return MaterialTheme.typography.bodyMedium.copy(
            fontSize = preferences.scaleFontSize(baseSize),
            fontWeight = fontWeight
        )
    }
}

// ---- Shared layout helpers ----

@Composable
private fun SectionColumn(
    title: String,
    titleBottomPadding: Dp = 12.dp,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = titleBottomPadding)
        )
        content()
    }
}

/**
 * Accent color picker composable with color wheel
 */
@Composable
fun AccentColorPicker(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }

    SectionColumn(title = "Accent Color", titleBottomPadding = 16.dp, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val position = change.position
                            val dx = position.x - center.x
                            val dy = position.y - center.y

                            hue = ((atan2(dy, dx) * 180 / Math.PI).toFloat() + 360) % 360

                            val distance = hypot(dx, dy)
                            val maxDistance = minOf(center.x, center.y)
                            saturation = (distance / maxDistance).coerceIn(0f, 1f)

                            onColorSelected(Color.hsv(hue, saturation, 1f))
                        }
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = minOf(center.x, center.y)

                for (angle in 0 until 360) {
                    val sweepGradient = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            Color.hsv(angle.toFloat(), 1f, 1f)
                        ),
                        center = center,
                        radius = radius
                    )

                    drawArc(
                        brush = sweepGradient,
                        startAngle = angle.toFloat(),
                        sweepAngle = 1.5f,
                        useCenter = true,
                        size = size
                    )
                }

                val selectedAngle = hue * Math.PI / 180
                val selectedDistance = saturation * radius
                val selectedPosition = Offset(
                    center.x + (cos(selectedAngle) * selectedDistance).toFloat(),
                    center.y + (sin(selectedAngle) * selectedDistance).toFloat()
                )

                drawCircle(
                    color = Color.White,
                    radius = 12f,
                    center = selectedPosition,
                    style = Stroke(width = 3f)
                )

                drawCircle(
                    color = selectedColor,
                    radius = 10f,
                    center = selectedPosition
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Presets",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PresetAccentColors) { color ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (color == selectedColor) 3.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(color) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Current",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(selectedColor)
            )
        }
    }
}

private val PresetAccentColors = listOf(
    GoldDark,
    ChartCyan,
    SuccessGreen,
    ChartPurple,
    ChartOrange,
    LiveBlue,
    Color(0xFFE91E63),
    Color(0xFF9C27B0)
)

/**
 * Animation intensity slider composable
 */
@Composable
fun AnimationIntensitySlider(
    intensity: AnimationIntensity,
    onIntensityChanged: (AnimationIntensity) -> Unit,
    reducedMotion: Boolean,
    onReducedMotionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val intensityLevels = AnimationIntensity.entries
    var sliderPosition by remember { mutableFloatStateOf(intensityLevels.indexOf(intensity).toFloat()) }

    SectionColumn(title = "Animation Intensity", titleBottomPadding = 8.dp, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "Reduce Motion",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )

            ToggleSwitch(
                checked = reducedMotion,
                onCheckedChange = onReducedMotionChanged
            )
        }

        if (!reducedMotion) {
            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = sliderPosition,
                onValueChange = { newValue ->
                    sliderPosition = newValue
                    val index = newValue.toInt().coerceIn(0, intensityLevels.size - 1)
                    onIntensityChanged(intensityLevels[index])
                },
                valueRange = 0f..(intensityLevels.size - 1).toFloat(),
                steps = intensityLevels.size - 2,
                colors = SliderDefaults.colors(
                    thumbColor = GoldDark,
                    activeTrackColor = GoldDark,
                    inactiveTrackColor = Color.White.copy(alpha = 0.08f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                intensityLevels.forEach { level ->
                    Text(
                        text = level.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (level == intensity) GoldDark else TextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Simple toggle switch composable
 */
@Composable
private fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (checked) GoldDark else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(200),
        label = "toggleBackground"
    )

    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(200),
        label = "toggleThumb"
    )

    Box(
        modifier = modifier
            .width(48.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.CenterStart)
                .padding(start = (thumbOffset * 24).dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * Card density selector
 */
@Composable
fun CardDensitySelector(
    selectedDensity: CardDensity,
    onDensitySelected: (CardDensity) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionColumn(title = "Card Density", modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CardDensity.entries.forEach { density ->
                DensityOptionCard(
                    density = density,
                    isSelected = density == selectedDensity,
                    onClick = { onDensitySelected(density) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DensityOptionCard(
    density: CardDensity,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) GoldDark else GlassBorder,
        animationSpec = tween(200),
        label = "densityBorder"
    )

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = if (isSelected) 0.08f else 0.05f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(density.spacing / 2)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(density.verticalPadding / 2)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isSelected) GoldSubtle else TextTertiary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = density.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) GoldDark else TextSecondary
            )
        }
    }
}

/**
 * Font size selector
 */
@Composable
fun FontSizeSelector(
    selectedSize: FontScaling,
    onSizeSelected: (FontScaling) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionColumn(title = "Text Size", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FontScaling.entries.forEach { scaling ->
                FontSizeOptionRow(
                    scaling = scaling,
                    isSelected = scaling == selectedSize,
                    onClick = { onSizeSelected(scaling) }
                )
            }
        }
    }
}

@Composable
private fun FontSizeOptionRow(
    scaling: FontScaling,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(200),
        label = "fontSizeBackground"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = scaling.label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = (14 * scaling.scale).sp
            ),
            color = if (isSelected) GoldDark else TextPrimary,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = scaling.description,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )

        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(GoldDark)
            )
        }
    }
}

/**
 * Preview container that shows live theme previews
 */
@Composable
fun PreviewContainer(
    preferences: AppPreferences,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(preferences.density.horizontalPadding)
        ) {
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(preferences.density.spacing))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = preferences.density.horizontalPadding,
                            vertical = preferences.density.verticalPadding
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "India",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = preferences.scaleFontSize(14.sp)
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = "245/6",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = preferences.scaleFontSize(18.sp),
                                color = preferences.accentColor
                            )
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "vs Australia",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = preferences.scaleFontSize(12.sp)
                            ),
                            color = TextSecondary
                        )
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = preferences.scaleFontSize(10.sp),
                                color = SuccessGreen
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(preferences.density.spacing))

            if (preferences.shouldAnimate()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Animations: ${preferences.animationIntensity.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            } else {
                Text(
                    text = "Animations disabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Preferences provider composable wrapper
 */
@Composable
fun AppPreferencesProvider(
    preferences: AppPreferences,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppPreferences provides preferences) {
        content()
    }
}

/**
 * Access current app preferences anywhere in the composition
 */
object Preferences {
    val current: AppPreferences
        @Composable
        get() = LocalAppPreferences.current
}

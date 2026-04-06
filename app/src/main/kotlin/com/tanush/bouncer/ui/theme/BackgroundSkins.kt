package com.tanush.bouncer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Bouncer Background Skins System
 * 
 * Premium background effects that provide visual identity and immersion.
 * Each skin is designed to complement the gold accent system while
 * maintaining readability and the premium sports broadcast aesthetic.
 * 
 * Skins are applied as background layers behind all content.
 */

// =============================================================================
// Background Skin Enum
// =============================================================================

/**
 * Available background skins for Bouncer.
 * Each skin provides a unique visual backdrop while maintaining brand consistency.
 */
enum class BackgroundSkinType(
    val displayName: String,
    val description: String,
    val isPremium: Boolean = false
) {
    /**
     * Default midnight gradient - Radial gradient from center-top.
     * Colors: #1A1A1A → #0F1115 → #000000
     */
    MIDNIGHT_GRADIENT(
        displayName = "Midnight Gradient",
        description = "Classic radial gradient with subtle gold bloom"
    ),
    
    /**
     * Stadium lights effect - Simulates floodlight ambiance.
     * Multiple light sources with soft glow.
     */
    STADIUM_LIGHTS(
        displayName = "Stadium Lights",
        description = "Floodlight ambiance for match-day feel",
        isPremium = true
    ),
    
    /**
     * Carbon fiber texture - Premium industrial aesthetic.
     * Subtle diagonal weave pattern.
     */
    CARBON_FIBER(
        displayName = "Carbon Fiber",
        description = "Premium carbon fiber texture",
        isPremium = true
    ),
    
    /**
     * Aurora effect - Subtle colorful northern lights.
     * Animated gradient shifts (optional).
     */
    AURORA(
        displayName = "Aurora",
        description = "Northern lights inspired gradient",
        isPremium = true
    ),
    
    /**
     * Pure black - Maximum contrast, minimal battery.
     * Best for AMOLED displays.
     */
    PURE_BLACK(
        displayName = "Pure Black",
        description = "Clean black for AMOLED displays"
    ),
    
    /**
     * Team colors - Dynamic background based on current match teams.
     * Uses team primary colors with subtle gradient.
     */
    TEAM_COLORS(
        displayName = "Team Colors",
        description = "Dynamic team-based background",
        isPremium = true
    )
}

// =============================================================================
// Background Skin Configuration
// =============================================================================

/**
 * Configuration for a background skin.
 * Contains all visual parameters needed to render the skin.
 */
@Immutable
data class BackgroundSkinConfig(
    val type: BackgroundSkinType,
    val primaryColor: Color = Color.Transparent,
    val secondaryColor: Color = Color.Transparent,
    val accentColor: Color = GoldPrimary,
    val intensity: Float = 1.0f,
    val animated: Boolean = false
)

// =============================================================================
// Background Skin Sealed Class
// =============================================================================

/**
 * Sealed class representing all background skin implementations.
 * Each skin contains its rendering logic and configuration.
 */
sealed class BackgroundSkin(
    open val config: BackgroundSkinConfig
) {
    /**
     * Midnight Gradient - Default premium background.
     */
    data class MidnightGradient(
        override val config: BackgroundSkinConfig = BackgroundSkinConfig(
            type = BackgroundSkinType.MIDNIGHT_GRADIENT
        ),
        val centerOffset: Float = 0.1f,
        val goldBloomAlpha: Float = 0.15f
    ) : BackgroundSkin(config)
    
    /**
     * Stadium Lights - Floodlight ambiance effect.
     */
    data class StadiumLights(
        override val config: BackgroundSkinConfig = BackgroundSkinConfig(
            type = BackgroundSkinType.STADIUM_LIGHTS
        ),
        val lightPositions: List<Offset> = listOf(
            Offset(0.2f, 0f),
            Offset(0.5f, 0f),
            Offset(0.8f, 0f)
        ),
        val lightColor: Color = Color.White,
        val lightAlpha: Float = 0.08f
    ) : BackgroundSkin(config)
    
    /**
     * Carbon Fiber - Premium industrial texture.
     */
    data class CarbonFiber(
        override val config: BackgroundSkinConfig = BackgroundSkinConfig(
            type = BackgroundSkinType.CARBON_FIBER
        ),
        val patternScale: Float = 1.0f,
        val weaveAlpha: Float = 0.06f
    ) : BackgroundSkin(config)
    
    /**
     * Aurora - Northern lights effect.
     */
    data class Aurora(
        override val config: BackgroundSkinConfig = BackgroundSkinConfig(
            type = BackgroundSkinType.AURORA,
            animated = true
        ),
        val colors: List<Color> = listOf(
            Color(0xFF22C55E).copy(alpha = 0.15f),
            Color(0xFF3B82F6).copy(alpha = 0.12f),
            Color(0xFF8B5CF6).copy(alpha = 0.10f)
        )
    ) : BackgroundSkin(config)
    
    /**
     * Pure Black - Clean AMOLED background.
     */
    data class PureBlack(
        override val config: BackgroundSkinConfig = BackgroundSkinConfig(
            type = BackgroundSkinType.PURE_BLACK
        )
    ) : BackgroundSkin(config)
    
    /**
     * Team Colors - Dynamic team-based background.
     */
    data class TeamColors(
        override val config: BackgroundSkinConfig = BackgroundSkinConfig(
            type = BackgroundSkinType.TEAM_COLORS
        ),
        val team1Color: Color = GoldPrimary,
        val team2Color: Color = Color(0xFF3B82F6),
        val blendMode: TeamColorBlendMode = TeamColorBlendMode.GRADIENT
    ) : BackgroundSkin(config)
    
    companion object {
        /**
         * Creates a background skin from the given type.
         */
        fun fromType(type: BackgroundSkinType): BackgroundSkin = when (type) {
            BackgroundSkinType.MIDNIGHT_GRADIENT -> MidnightGradient()
            BackgroundSkinType.STADIUM_LIGHTS -> StadiumLights()
            BackgroundSkinType.CARBON_FIBER -> CarbonFiber()
            BackgroundSkinType.AURORA -> Aurora()
            BackgroundSkinType.PURE_BLACK -> PureBlack()
            BackgroundSkinType.TEAM_COLORS -> TeamColors()
        }
    }
}

/**
 * Blend modes for team color backgrounds.
 */
enum class TeamColorBlendMode {
    GRADIENT,      // Horizontal gradient between team colors
    SPLIT,         // Diagonal split
    DOMINANT,      // Single dominant team color
    SUBTLE_GLOW    // Subtle glow from corners
}

// =============================================================================
// CompositionLocal
// =============================================================================

/**
 * CompositionLocal for the current background skin.
 */
val LocalBackgroundSkin = staticCompositionLocalOf<BackgroundSkin> { BackgroundSkin.MidnightGradient() }

// =============================================================================
// Background Skin Accessor
// =============================================================================

/**
 * Accessor object for background skin information.
 */
object BackgroundSkins {
    /**
     * Current background skin.
     */
    val current: BackgroundSkin
        @Composable
        @ReadOnlyComposable
        get() = LocalBackgroundSkin.current
    
    /**
     * Current background skin type.
     */
    val currentType: BackgroundSkinType
        @Composable
        @ReadOnlyComposable
        get() = LocalBackgroundSkin.current.config.type
    
    /**
     * All available background skin types.
     */
    val all: List<BackgroundSkinType> = BackgroundSkinType.entries
    
    /**
     * Premium background skin types only.
     */
    val premium: List<BackgroundSkinType> = BackgroundSkinType.entries.filter { it.isPremium }
    
    /**
     * Free background skin types.
     */
    val free: List<BackgroundSkinType> = BackgroundSkinType.entries.filter { !it.isPremium }
}

// =============================================================================
// Background Skin Composables
// =============================================================================

/**
 * Renders the background skin as a full-screen layer.
 * This should be placed as the first element in the theme's content.
 */
@Composable
fun BackgroundSkinLayer(
    skin: BackgroundSkin,
    modifier: Modifier = Modifier
) {
    when (skin) {
        is BackgroundSkin.MidnightGradient -> MidnightGradientBackground(skin, modifier)
        is BackgroundSkin.StadiumLights -> StadiumLightsBackground(skin, modifier)
        is BackgroundSkin.CarbonFiber -> CarbonFiberBackground(skin, modifier)
        is BackgroundSkin.Aurora -> AuroraBackground(skin, modifier)
        is BackgroundSkin.PureBlack -> PureBlackBackground(skin, modifier)
        is BackgroundSkin.TeamColors -> TeamColorsBackground(skin, modifier)
    }
}

/**
 * Midnight Gradient background implementation.
 * Default premium background with radial gradient and gold bloom.
 */
@Composable
private fun MidnightGradientBackground(
    skin: BackgroundSkin.MidnightGradient,
    modifier: Modifier = Modifier
) {
    val palette = LocalVariantPalette.current
    
    Box(modifier = modifier.fillMaxSize()) {
        // Base black layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        
        // Stadium gradient layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            0.0f to Color(0xFF1A1A1A),
                            0.6f to palette.backgroundBase,
                            1.0f to Color.Black,
                            center = Offset(size.width / 2f, size.height * skin.centerOffset),
                            radius = size.height * 0.8f
                        )
                    )
                }
        )
        
        // Gold bloom layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            0.0f to GoldPrimary.copy(alpha = skin.goldBloomAlpha * skin.config.intensity),
                            1.0f to Color.Transparent,
                            center = Offset(size.width / 2f, -size.height * 0.05f),
                            radius = size.width * 1.2f
                        )
                    )
                }
        )
        
        // Noise overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.04f))
        )
    }
}

/**
 * Stadium Lights background implementation.
 * Simulates floodlight ambiance with multiple light sources.
 */
@Composable
private fun StadiumLightsBackground(
    skin: BackgroundSkin.StadiumLights,
    modifier: Modifier = Modifier
) {
    val palette = LocalVariantPalette.current
    
    Box(modifier = modifier.fillMaxSize()) {
        // Base dark layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.backgroundBase)
        )
        
        // Light beams
        skin.lightPositions.forEach { position ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(120.dp)
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                0.0f to skin.lightColor.copy(alpha = skin.lightAlpha * skin.config.intensity),
                                0.5f to skin.lightColor.copy(alpha = skin.lightAlpha * 0.3f * skin.config.intensity),
                                1.0f to Color.Transparent,
                                center = Offset(size.width * position.x, size.height * position.y),
                                radius = size.width * 0.6f
                            )
                        )
                    }
            )
        }
        
        // Gold accent glow at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(100.dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            0.0f to GoldPrimary.copy(alpha = 0.08f * skin.config.intensity),
                            1.0f to Color.Transparent,
                            center = Offset(size.width / 2f, size.height * 1.1f),
                            radius = size.width * 0.8f
                        )
                    )
                }
        )
    }
}

/**
 * Carbon Fiber background implementation.
 * Premium industrial texture with diagonal weave pattern.
 */
@Composable
private fun CarbonFiberBackground(
    skin: BackgroundSkin.CarbonFiber,
    modifier: Modifier = Modifier
) {
    val palette = LocalVariantPalette.current
    
    Box(modifier = modifier.fillMaxSize()) {
        // Base layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.backgroundBase)
        )
        
        // Carbon fiber pattern simulation using diagonal gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Horizontal weave lines
                    val lineSpacing = 4.dp.toPx() * skin.patternScale
                    val lineAlpha = skin.weaveAlpha * skin.config.intensity
                    
                    for (y in 0..(size.height / lineSpacing).toInt()) {
                        val yPos = y * lineSpacing
                        drawLine(
                            color = Color.White.copy(alpha = lineAlpha),
                            start = Offset(0f, yPos),
                            end = Offset(size.width, yPos),
                            strokeWidth = 0.5.dp.toPx()
                        )
                    }
                    
                    // Diagonal weave pattern
                    for (x in (-size.height.toInt())..(size.width.toInt()) step (lineSpacing * 2).toInt()) {
                        drawLine(
                            color = Color.White.copy(alpha = lineAlpha * 0.5f),
                            start = Offset(x.toFloat(), 0f),
                            end = Offset(x + size.height, size.height),
                            strokeWidth = 0.3.dp.toPx()
                        )
                    }
                }
        )
        
        // Vignette overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            0.0f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.4f),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width * 0.8f
                        )
                    )
                }
        )
    }
}

/**
 * Aurora background implementation.
 * Northern lights inspired gradient effect.
 */
@Composable
private fun AuroraBackground(
    skin: BackgroundSkin.Aurora,
    modifier: Modifier = Modifier
) {
    val palette = LocalVariantPalette.current
    
    Box(modifier = modifier.fillMaxSize()) {
        // Base dark layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.backgroundBase)
        )
        
        // Aurora color bands
        skin.colors.forEachIndexed { index, color ->
            val offset = 0.2f + (index * 0.25f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur((80 + index * 20).dp)
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                offset to color.copy(alpha = color.alpha * skin.config.intensity),
                                (offset + 0.2f) to Color.Transparent
                            )
                        )
                    }
            )
        }
        
        // Gold accent at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(60.dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.9f to GoldPrimary.copy(alpha = 0.06f * skin.config.intensity),
                            1.0f to GoldPrimary.copy(alpha = 0.12f * skin.config.intensity)
                        )
                    )
                }
        )
    }
}

/**
 * Pure Black background implementation.
 * Clean AMOLED-friendly background.
 */
@Composable
private fun PureBlackBackground(
    skin: BackgroundSkin.PureBlack,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}

/**
 * Team Colors background implementation.
 * Dynamic team-based gradient background.
 */
@Composable
private fun TeamColorsBackground(
    skin: BackgroundSkin.TeamColors,
    modifier: Modifier = Modifier
) {
    val palette = LocalVariantPalette.current
    
    Box(modifier = modifier.fillMaxSize()) {
        // Base dark layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.backgroundBase)
        )
        
        when (skin.blendMode) {
            TeamColorBlendMode.GRADIENT -> {
                // Horizontal gradient between team colors
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(100.dp)
                        .drawBehind {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0.0f to skin.team1Color.copy(alpha = 0.15f * skin.config.intensity),
                                    0.5f to Color.Transparent,
                                    1.0f to skin.team2Color.copy(alpha = 0.15f * skin.config.intensity)
                                )
                            )
                        }
                )
            }
            TeamColorBlendMode.SPLIT -> {
                // Diagonal split
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(80.dp)
                        .drawBehind {
                            drawRect(
                                brush = Brush.linearGradient(
                                    0.0f to skin.team1Color.copy(alpha = 0.12f * skin.config.intensity),
                                    0.45f to skin.team1Color.copy(alpha = 0.08f * skin.config.intensity),
                                    0.55f to skin.team2Color.copy(alpha = 0.08f * skin.config.intensity),
                                    1.0f to skin.team2Color.copy(alpha = 0.12f * skin.config.intensity),
                                    start = Offset.Zero,
                                    end = Offset(size.width, size.height)
                                )
                            )
                        }
                )
            }
            TeamColorBlendMode.DOMINANT -> {
                // Single dominant team color
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(120.dp)
                        .drawBehind {
                            drawRect(
                                brush = Brush.radialGradient(
                                    0.0f to skin.team1Color.copy(alpha = 0.18f * skin.config.intensity),
                                    1.0f to Color.Transparent,
                                    center = Offset(size.width / 2f, size.height * 0.3f),
                                    radius = size.width * 0.8f
                                )
                            )
                        }
                )
            }
            TeamColorBlendMode.SUBTLE_GLOW -> {
                // Corner glows
                listOf(
                    Offset(0f, 0f) to skin.team1Color,
                    Offset(1f, 1f) to skin.team2Color
                ).forEach { (position, color) ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(150.dp)
                            .drawBehind {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        0.0f to color.copy(alpha = 0.12f * skin.config.intensity),
                                        1.0f to Color.Transparent,
                                        center = Offset(size.width * position.x, size.height * position.y),
                                        radius = size.width * 0.6f
                                    )
                                )
                            }
                    )
                }
            }
        }
        
        // Gold accent overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            0.0f to GoldPrimary.copy(alpha = 0.08f * skin.config.intensity),
                            1.0f to Color.Transparent,
                            center = Offset(size.width / 2f, -size.height * 0.1f),
                            radius = size.width * 1.0f
                        )
                    )
                }
        )
    }
}

// =============================================================================
// Utility Functions
// =============================================================================

/**
 * Creates a team colors background skin with the specified team colors.
 */
fun createTeamColorsSkin(
    team1Color: Color,
    team2Color: Color,
    blendMode: TeamColorBlendMode = TeamColorBlendMode.GRADIENT,
    intensity: Float = 1.0f
): BackgroundSkin.TeamColors = BackgroundSkin.TeamColors(
    config = BackgroundSkinConfig(
        type = BackgroundSkinType.TEAM_COLORS,
        primaryColor = team1Color,
        secondaryColor = team2Color,
        intensity = intensity
    ),
    team1Color = team1Color,
    team2Color = team2Color,
    blendMode = blendMode
)

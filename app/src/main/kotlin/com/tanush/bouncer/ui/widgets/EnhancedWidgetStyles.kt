package com.tanush.bouncer.ui.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider

/**
 * EnhancedWidgetStyles - Premium Widget Styling System
 * 
 * Provides Material 3 inspired styling for Glance widgets with:
 * - Consistent color theming (light/dark mode support)
 * - Typography system for widget text
 * - Shadow and depth approximations
 * - Layout templates for different widget sizes
 */

// ===========================================
// WIDGET COLOR SYSTEM
// ===========================================

/**
 * Widget color scheme supporting light and dark modes
 */
object WidgetColorScheme {
    // Core Backgrounds
    val background = ColorProvider(
        day = Color(0xFFF8F9FA),
        night = Color(0xFF050505)
    )
    
    val surface = ColorProvider(
        day = Color(0xFFFFFFFF),
        night = Color(0xFF0F0F0F)
    )
    
    val surfaceElevated = ColorProvider(
        day = Color(0xFFF0F0F0),
        night = Color(0xFF141414)
    )
    
    val surfaceHighest = ColorProvider(
        day = Color(0xFFE8E8E8),
        night = Color(0xFF1A1A1A)
    )
    
    // Primary Gold Accent
    val primary = ColorProvider(
        day = Color(0xFFB39800),
        night = Color(0xFFFFD700)
    )
    
    val primaryDim = ColorProvider(
        day = Color(0xFF8B7500),
        night = Color(0xFFB39800)
    )
    
    val primaryContainer = ColorProvider(
        day = Color(0xFFFFF4C4),
        night = Color(0xFF2A2000)
    )
    
    // Text Colors
    val textPrimary = ColorProvider(
        day = Color(0xFF1A1A1A),
        night = Color(0xFFFFFFFF)
    )
    
    val textSecondary = ColorProvider(
        day = Color(0xFF64748B),
        night = Color(0xFF94A3B8)
    )
    
    val textTertiary = ColorProvider(
        day = Color(0xFF94A3B8),
        night = Color(0xFF64748B)
    )
    
    val textOnPrimary = ColorProvider(
        day = Color(0xFF1A1A1A),
        night = Color(0xFF1A1A1A)
    )
    
    // Semantic Colors
    val success = ColorProvider(
        day = Color(0xFF16A34A),
        night = Color(0xFF22C55E)
    )
    
    val error = ColorProvider(
        day = Color(0xFFDC2626),
        night = Color(0xFFEF4444)
    )
    
    val warning = ColorProvider(
        day = Color(0xFFD97706),
        night = Color(0xFFFACC15)
    )
    
    val live = ColorProvider(
        day = Color(0xFFDC2626),
        night = Color(0xFFFF3B30)
    )
    
    // Event Colors
    val wicket = ColorProvider(
        day = Color(0xFFDC2626),
        night = Color(0xFFFF3B30)
    )
    
    val six = ColorProvider(
        day = Color(0xFF7C3AED),
        night = Color(0xFF8B5CF6)
    )
    
    val four = ColorProvider(
        day = Color(0xFF16A34A),
        night = Color(0xFF22C55E)
    )
    
    val dotBall = ColorProvider(
        day = Color(0xFFD1D5DB),
        night = Color(0xFF454545)
    )
    
    // Border Colors
    val border = ColorProvider(
        day = Color(0xFFE5E7EB),
        night = Color(0x14FFFFFF)
    )
    
    val borderAccent = ColorProvider(
        day = Color(0xFFB39800),
        night = Color(0x33FFD700)
    )
}

// ===========================================
// WIDGET TYPOGRAPHY
// ===========================================

/**
 * Typography system for widgets with predefined text styles
 */
object WidgetTypography {
    
    val displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val headlineMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val headlineSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = WidgetColorScheme.textSecondary
    )
    
    val bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = WidgetColorScheme.textSecondary
    )
    
    val bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = WidgetColorScheme.textSecondary
    )
    
    val labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = WidgetColorScheme.textTertiary
    )
    
    val labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        color = WidgetColorScheme.textTertiary
    )
    
    val labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        color = WidgetColorScheme.textTertiary
    )
    
    // Score-specific styles
    val scoreLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val scoreMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val scoreSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val teamName = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val teamAcronym = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = WidgetColorScheme.textSecondary
    )
    
    val statusLive = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        color = WidgetColorScheme.live
    )
    
    val statusNormal = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = WidgetColorScheme.textTertiary
    )
    
    val winProbability = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = WidgetColorScheme.primary
    )
}

// ===========================================
// WIDGET THEME
// ===========================================

/**
 * Widget theme object containing all styling constants
 */
object WidgetTheme {
    // Corner radii
    val radiusSmall = 8.dp
    val radiusMedium = 12.dp
    val radiusLarge = 16.dp
    val radiusXLarge = 20.dp
    val radiusFull = 100.dp
    
    // Spacing
    val spacingXSmall = 4.dp
    val spacingSmall = 8.dp
    val spacingMedium = 12.dp
    val spacingLarge = 16.dp
    val spacingXLarge = 20.dp
    val spacingXXLarge = 24.dp
    
    // Padding
    val paddingSmall = 8.dp
    val paddingMedium = 12.dp
    val paddingLarge = 16.dp
    
    // Icon sizes
    val iconSizeSmall = 16.dp
    val iconSizeMedium = 20.dp
    val iconSizeLarge = 24.dp
    val iconSizeXLarge = 32.dp
    
    // Team logo sizes
    val teamLogoSmall = 24.dp
    val teamLogoMedium = 32.dp
    val teamLogoLarge = 40.dp
    val teamLogoXLarge = 48.dp
    
    // Ball indicator size
    val ballIndicatorSize = 22.dp
    
    // Progress bar heights
    val progressBarHeightSmall = 4.dp
    val progressBarHeightMedium = 6.dp
    val progressBarHeightLarge = 8.dp
    
    // Widget minimum dimensions
    val smallWidgetMinWidth = 110.dp
    val smallWidgetMinHeight = 110.dp
    val mediumWidgetMinWidth = 180.dp
    val mediumWidgetMinHeight = 110.dp
    val largeWidgetMinWidth = 250.dp
    val largeWidgetMinHeight = 180.dp
}

// ===========================================
// GLANCE MODIFIER EXTENSIONS
// ===========================================

/**
 * Extension for applying rounded corners with consistent styling
 */
fun GlanceModifier.widgetCornerRadius(radius: Dp = WidgetTheme.radiusMedium): GlanceModifier {
    return this.cornerRadius(radius)
}

/**
 * Extension for card-like appearance with background and rounded corners
 */
fun GlanceModifier.widgetCard(
    backgroundColor: GlanceColorProvider = WidgetColorScheme.surface,
    cornerRadius: Dp = WidgetTheme.radiusMedium
): GlanceModifier {
    return this
        .background(backgroundColor)
        .cornerRadius(cornerRadius)
}

/**
 * Extension for elevated card with shadow approximation
 */
fun GlanceModifier.widgetCardElevated(
    backgroundColor: GlanceColorProvider = WidgetColorScheme.surfaceElevated,
    cornerRadius: Dp = WidgetTheme.radiusMedium
): GlanceModifier {
    return this
        .background(backgroundColor)
        .cornerRadius(cornerRadius)
}

/**
 * Extension for primary accent card styling
 */
fun GlanceModifier.widgetCardAccent(
    backgroundColor: GlanceColorProvider = WidgetColorScheme.primaryContainer,
    cornerRadius: Dp = WidgetTheme.radiusMedium
): GlanceModifier {
    return this
        .background(backgroundColor)
        .cornerRadius(cornerRadius)
}

// ===========================================
// WIDGET SHADOW APPROXIMATION
// ===========================================

/**
 * Shadow approximation using layered backgrounds
 * Since Glance doesn't support real shadows, we use stacked containers
 */
object WidgetShadow {
    
    val shadowColorLight = Color(0x1A000000)
    val shadowColorMedium = Color(0x26000000)
    val shadowColorDark = Color(0x33000000)
    
    /**
     * Creates shadow-like depth using nested containers
     */
    @Composable
    fun ShadowedContainer(
        modifier: GlanceModifier = GlanceModifier,
        shadowColor: GlanceColorProvider = ColorProvider(
            day = shadowColorLight,
            night = Color(0x00000000)
        ),
        contentBackgroundColor: GlanceColorProvider = WidgetColorScheme.surface,
        cornerRadius: Dp = WidgetTheme.radiusMedium,
        content: @Composable () -> Unit
    ) {
        // Outer shadow layer (only visible in light mode)
        Box(
            modifier = modifier
                .background(shadowColor)
                .cornerRadius(cornerRadius + 2.dp)
                .padding(bottom = 2.dp, end = 2.dp)
        ) {
            // Inner content container
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(contentBackgroundColor)
                    .cornerRadius(cornerRadius)
            ) {
                content()
            }
        }
    }
    
    /**
     * Subtle inner glow effect using gradient-like approach
     */
    @Composable
    fun GlowContainer(
        modifier: GlanceModifier = GlanceModifier,
        glowColor: GlanceColorProvider = WidgetColorScheme.primaryContainer,
        contentBackgroundColor: GlanceColorProvider = WidgetColorScheme.surface,
        cornerRadius: Dp = WidgetTheme.radiusMedium,
        content: @Composable () -> Unit
    ) {
        // Outer glow layer
        Box(
            modifier = modifier
                .background(glowColor)
                .cornerRadius(cornerRadius + 1.dp)
                .padding(1.dp)
        ) {
            // Inner content container
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(contentBackgroundColor)
                    .cornerRadius(cornerRadius)
            ) {
                content()
            }
        }
    }
}

// ===========================================
// WIDGET LAYOUT TEMPLATES
// ===========================================

/**
 * Small widget layout template (2x2 cells, ~110x110 dp)
 * Compact view with team scores only
 */
@Composable
fun SmallWidgetLayout(
    modifier: GlanceModifier = GlanceModifier,
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background,
    header: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
    footer: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusLarge)
            .padding(WidgetTheme.paddingSmall)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            // Optional header (live indicator, match type)
            header?.let {
                Box(modifier = GlanceModifier.padding(bottom = WidgetTheme.spacingXSmall)) {
                    it()
                }
            }
            
            // Main content (scores)
            Box(modifier = GlanceModifier.defaultWeight()) {
                content()
            }
            
            // Optional footer (status, quick action)
            footer?.let {
                Box(modifier = GlanceModifier.padding(top = WidgetTheme.spacingXSmall)) {
                    it()
                }
            }
        }
    }
}

/**
 * Medium widget layout template (4x2 cells, ~180x110 dp)
 * Shows team details with overs and status
 */
@Composable
fun MediumWidgetLayout(
    modifier: GlanceModifier = GlanceModifier,
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background,
    header: @Composable (() -> Unit)? = null,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
    footer: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusLarge)
            .padding(WidgetTheme.paddingMedium)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            // Optional header
            header?.let {
                Box(modifier = GlanceModifier.padding(bottom = WidgetTheme.spacingSmall)) {
                    it()
                }
            }
            
            // Main content - two columns
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Left team
                Box(modifier = GlanceModifier.defaultWeight()) {
                    leftContent()
                }
                
                // VS divider
                Box(
                    modifier = GlanceModifier.padding(horizontal = WidgetTheme.spacingSmall),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.glance.text.Text(
                        text = "vs",
                        style = WidgetTypography.labelMedium
                    )
                }
                
                // Right team
                Box(modifier = GlanceModifier.defaultWeight()) {
                    rightContent()
                }
            }
            
            // Optional footer
            footer?.let {
                Box(modifier = GlanceModifier.padding(top = WidgetTheme.spacingSmall)) {
                    it()
                }
            }
        }
    }
}

/**
 * Large widget layout template (4x3 or larger cells, ~250x180 dp)
 * Full scorecard with recent balls and win probability
 */
@Composable
fun LargeWidgetLayout(
    modifier: GlanceModifier = GlanceModifier,
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background,
    header: @Composable () -> Unit,
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable (() -> Unit)? = null,
    footer: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusLarge)
            .padding(WidgetTheme.paddingLarge)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            // Header section (match info, live indicator)
            Box(modifier = GlanceModifier.padding(bottom = WidgetTheme.spacingMedium)) {
                header()
            }
            
            // Primary content (team scores, overs)
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
            ) {
                primaryContent()
            }
            
            // Secondary content (recent balls, batsmen info)
            secondaryContent?.let {
                Box(modifier = GlanceModifier.padding(vertical = WidgetTheme.spacingSmall)) {
                    it()
                }
            }
            
            // Footer (win probability, actions)
            Box(modifier = GlanceModifier.padding(top = WidgetTheme.spacingMedium)) {
                footer()
            }
        }
    }
}

// ===========================================
// GRADIENT APPROXIMATIONS
// ===========================================

/**
 * Gradient approximations for widget backgrounds
 * Since Glance doesn't support gradients directly, we use solid colors
 * that represent the gradient's dominant color
 */
object WidgetGradients {
    
    /**
     * Gold gradient approximation - uses mid-tone gold
     */
    val goldGradient = WidgetColorScheme.primary
    
    /**
     * Success gradient approximation
     */
    val successGradient = WidgetColorScheme.success
    
    /**
     * Error gradient approximation
     */
    val errorGradient = WidgetColorScheme.error
    
    /**
     * Surface gradient approximation - uses elevated surface
     */
    val surfaceGradient = WidgetColorScheme.surfaceElevated
    
    /**
     * Glass effect approximation - semi-transparent surface
     */
    val glassEffect = ColorProvider(
        day = Color(0xE6FFFFFF),
        night = Color(0x99141414)
    )
}

// ===========================================
// WIDGET DIMENSION HELPERS
// ===========================================

/**
 * Helper object for calculating responsive widget dimensions
 */
object WidgetDimensions {
    
    /**
     * Get team logo size based on widget size
     */
    fun teamLogoSize(isSmallWidget: Boolean): Dp {
        return if (isSmallWidget) WidgetTheme.teamLogoSmall else WidgetTheme.teamLogoMedium
    }
    
    /**
     * Get score text style based on widget size
     */
    fun scoreTextStyle(isSmallWidget: Boolean): TextStyle {
        return if (isSmallWidget) WidgetTypography.scoreSmall else WidgetTypography.scoreMedium
    }
    
    /**
     * Get padding based on widget size
     */
    fun contentPadding(isSmallWidget: Boolean): Dp {
        return if (isSmallWidget) WidgetTheme.paddingSmall else WidgetTheme.paddingMedium
    }
    
    /**
     * Get spacing based on widget size
     */
    fun contentSpacing(isSmallWidget: Boolean): Dp {
        return if (isSmallWidget) WidgetTheme.spacingXSmall else WidgetTheme.spacingSmall
    }
}

// ===========================================
// GLASSMORPHISM EFFECTS
// ===========================================

/**
 * Glassmorphism styling for modern widget appearance
 */
object WidgetGlassmorphism {
    
    /**
     * Glass background with blur simulation
     */
    val glassBackground = ColorProvider(
        day = Color(0xCCFFFFFF),
        night = Color(0x80141414)
    )
    
    /**
     * Frosted glass with higher opacity
     */
    val frostedGlass = ColorProvider(
        day = Color(0xE6F8F9FA),
        night = Color(0xB30F0F0F)
    )
    
    /**
     * Glass border/stroke color
     */
    val glassBorder = ColorProvider(
        day = Color(0x33FFFFFF),
        night = Color(0x1AFFFFFF)
    )
    
    /**
     * Glass card modifier
     */
    fun GlanceModifier.glassCard(
        cornerRadius: Dp = WidgetTheme.radiusMedium
    ): GlanceModifier {
        return this
            .background(glassBackground)
            .cornerRadius(cornerRadius)
    }
    
    /**
     * Frosted card modifier with higher opacity
     */
    fun GlanceModifier.frostedCard(
        cornerRadius: Dp = WidgetTheme.radiusMedium
    ): GlanceModifier {
        return this
            .background(frostedGlass)
            .cornerRadius(cornerRadius)
    }
}

// ===========================================
// ANIMATION SIMULATION STYLES
// ===========================================

/**
 * Animation simulation through color alternation
 * Since Glance doesn't support true animations,
 * these simulate movement through state changes
 */
object WidgetAnimationStyles {
    
    /**
     * Pulse animation colors (cycle through on refresh)
     */
    val pulseColors = listOf(
        WidgetColorScheme.live,
        ColorProvider(
            day = Color(0xFFFF6B6B),
            night = Color(0xFFFF5252)
        ),
        ColorProvider(
            day = Color(0xFFDC2626),
            night = Color(0xFFFF3B30)
        )
    )
    
    /**
     * Get pulse color based on update cycle
     */
    fun getPulseColor(cycleIndex: Int): GlanceColorProvider {
        return pulseColors[cycleIndex % pulseColors.size]
    }
    
    /**
     * Shimmer effect colors for loading states
     */
    val shimmerBase = ColorProvider(
        day = Color(0xFFE5E7EB),
        night = Color(0xFF2A2A2A)
    )
    
    val shimmerHighlight = ColorProvider(
        day = Color(0xFFF3F4F6),
        night = Color(0xFF3A3A3A)
    )
    
    /**
     * Loading placeholder modifier
     */
    fun GlanceModifier.shimmerPlaceholder(
        cornerRadius: Dp = WidgetTheme.radiusSmall
    ): GlanceModifier {
        return this
            .background(shimmerBase)
            .cornerRadius(cornerRadius)
    }
}

// ===========================================
// COMPACT SCORE DISPLAY STYLES
// ===========================================

/**
 * Ultra-compact styles for small widgets
 */
object CompactWidgetStyles {
    
    val compactScoreStyle = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = WidgetColorScheme.textPrimary
    )
    
    val compactTeamStyle = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        color = WidgetColorScheme.textSecondary
    )
    
    val compactLabelStyle = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 8.sp,
        color = WidgetColorScheme.textTertiary
    )
    
    val ultraCompactSpacing = 2.dp
    val compactPadding = 4.dp
    val compactCornerRadius = 6.dp
}

// ===========================================
// EXTRA LARGE WIDGET LAYOUT
// ===========================================

/**
 * Extra large widget layout (5x4 cells or full-width)
 * Full scorecard with detailed stats
 */
@Composable
fun ExtraLargeWidgetLayout(
    modifier: GlanceModifier = GlanceModifier,
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background,
    header: @Composable () -> Unit,
    scoreboard: @Composable () -> Unit,
    battingCard: @Composable (() -> Unit)? = null,
    bowlingCard: @Composable (() -> Unit)? = null,
    statsRow: @Composable (() -> Unit)? = null,
    footer: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusXLarge)
            .padding(WidgetTheme.spacingXXLarge)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            // Header with match info and live indicator
            Box(modifier = GlanceModifier.padding(bottom = WidgetTheme.spacingMedium)) {
                header()
            }
            
            // Main scoreboard section
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .widgetCard()
                    .padding(WidgetTheme.paddingMedium)
            ) {
                scoreboard()
            }
            
            // Stats cards row
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = WidgetTheme.spacingMedium),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                // Batting card
                battingCard?.let {
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .widgetCardElevated()
                            .padding(WidgetTheme.paddingSmall)
                    ) {
                        it()
                    }
                }
                
                Spacer(modifier = GlanceModifier.width(WidgetTheme.spacingSmall))
                
                // Bowling card
                bowlingCard?.let {
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .widgetCardElevated()
                            .padding(WidgetTheme.paddingSmall)
                    ) {
                        it()
                    }
                }
            }
            
            // Additional stats row
            statsRow?.let {
                Box(modifier = GlanceModifier.padding(vertical = WidgetTheme.spacingSmall)) {
                    it()
                }
            }
            
            // Footer with actions and probability
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = WidgetTheme.spacingMedium)
            ) {
                footer()
            }
        }
    }
}

// ===========================================
// RESPONSIVE SIZE CALCULATOR
// ===========================================

/**
 * Calculates responsive dimensions based on widget size class
 */
object ResponsiveSizing {
    
    enum class WidgetSizeClass {
        SMALL,      // 2x2 cells
        MEDIUM,     // 4x2 cells
        LARGE,      // 4x3 cells
        EXTRA_LARGE // 5x4+ cells
    }
    
    /**
     * Get appropriate text style for score based on widget size
     */
    fun getScoreStyle(sizeClass: WidgetSizeClass): TextStyle {
        return when (sizeClass) {
            WidgetSizeClass.SMALL -> WidgetTypography.scoreSmall
            WidgetSizeClass.MEDIUM -> WidgetTypography.scoreMedium
            WidgetSizeClass.LARGE -> WidgetTypography.scoreLarge
            WidgetSizeClass.EXTRA_LARGE -> WidgetTypography.displaySmall
        }
    }
    
    /**
     * Get team name style based on widget size
     */
    fun getTeamNameStyle(sizeClass: WidgetSizeClass): TextStyle {
        return when (sizeClass) {
            WidgetSizeClass.SMALL -> WidgetTypography.labelMedium
            WidgetSizeClass.MEDIUM -> WidgetTypography.titleSmall
            WidgetSizeClass.LARGE -> WidgetTypography.titleMedium
            WidgetSizeClass.EXTRA_LARGE -> WidgetTypography.titleLarge
        }
    }
    
    /**
     * Get padding based on widget size
     */
    fun getPadding(sizeClass: WidgetSizeClass): Dp {
        return when (sizeClass) {
            WidgetSizeClass.SMALL -> WidgetTheme.paddingSmall
            WidgetSizeClass.MEDIUM -> WidgetTheme.paddingMedium
            WidgetSizeClass.LARGE -> WidgetTheme.paddingLarge
            WidgetSizeClass.EXTRA_LARGE -> WidgetTheme.spacingXXLarge
        }
    }
    
    /**
     * Get logo size based on widget size
     */
    fun getLogoSize(sizeClass: WidgetSizeClass): Dp {
        return when (sizeClass) {
            WidgetSizeClass.SMALL -> WidgetTheme.teamLogoSmall
            WidgetSizeClass.MEDIUM -> WidgetTheme.teamLogoMedium
            WidgetSizeClass.LARGE -> WidgetTheme.teamLogoLarge
            WidgetSizeClass.EXTRA_LARGE -> WidgetTheme.teamLogoXLarge
        }
    }
    
    /**
     * Get corner radius based on widget size
     */
    fun getCornerRadius(sizeClass: WidgetSizeClass): Dp {
        return when (sizeClass) {
            WidgetSizeClass.SMALL -> WidgetTheme.radiusMedium
            WidgetSizeClass.MEDIUM -> WidgetTheme.radiusLarge
            WidgetSizeClass.LARGE -> WidgetTheme.radiusLarge
            WidgetSizeClass.EXTRA_LARGE -> WidgetTheme.radiusXLarge
        }
    }
    
    /**
     * Get max balls to show in recent balls strip
     */
    fun getMaxBalls(sizeClass: WidgetSizeClass): Int {
        return when (sizeClass) {
            WidgetSizeClass.SMALL -> 3
            WidgetSizeClass.MEDIUM -> 6
            WidgetSizeClass.LARGE -> 6
            WidgetSizeClass.EXTRA_LARGE -> 12
        }
    }
    
    /**
     * Whether to show extended info
     */
    fun showExtendedInfo(sizeClass: WidgetSizeClass): Boolean {
        return sizeClass == WidgetSizeClass.LARGE || sizeClass == WidgetSizeClass.EXTRA_LARGE
    }
}

// ===========================================
// DYNAMIC THEME PROVIDER
// ===========================================

/**
 * Provides dynamic colors based on team colors or user preference
 */
object DynamicWidgetTheme {
    
    /**
     * Creates a team-based color provider
     */
    fun teamColorProvider(
        teamColor: Color,
        fallbackDay: Color = Color(0xFFB39800),
        fallbackNight: Color = Color(0xFFFFD700)
    ): GlanceColorProvider {
        return ColorProvider(
            day = teamColor.takeIf { it != Color.Transparent } ?: fallbackDay,
            night = teamColor.takeIf { it != Color.Transparent } ?: fallbackNight
        )
    }
    
    /**
     * Creates accent-adjusted surface color
     */
    fun accentSurface(accentColor: Color, alpha: Float = 0.1f): GlanceColorProvider {
        return ColorProvider(
            day = accentColor.copy(alpha = alpha),
            night = accentColor.copy(alpha = alpha * 0.5f)
        )
    }
    
    /**
     * Match format specific colors
     */
    object MatchFormatColors {
        val test = ColorProvider(
            day = Color(0xFF8B4513),
            night = Color(0xFFCD853F)
        )
        val odi = ColorProvider(
            day = Color(0xFF1E3A8A),
            night = Color(0xFF3B82F6)
        )
        val t20 = ColorProvider(
            day = Color(0xFF7C3AED),
            night = Color(0xFF8B5CF6)
        )
        val t10 = ColorProvider(
            day = Color(0xFFEC4899),
            night = Color(0xFFF472B6)
        )
        val theHundred = ColorProvider(
            day = Color(0xFF059669),
            night = Color(0xFF10B981)
        )
        val default = WidgetColorScheme.primary
        
        fun getFormatColor(format: String): GlanceColorProvider {
            return when (format.lowercase()) {
                "test" -> test
                "odi" -> odi
                "t20", "t20i" -> t20
                "t10" -> t10
                "100", "hundred", "the hundred" -> theHundred
                else -> default
            }
        }
    }
}

// ===========================================
// LOADING STATE LAYOUTS
// ===========================================

/**
 * Loading state placeholder for widgets
 */
@Composable
fun WidgetLoadingLayout(
    modifier: GlanceModifier = GlanceModifier,
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusLarge)
            .padding(WidgetTheme.paddingMedium),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Loading indicator simulation
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .background(WidgetAnimationStyles.shimmerBase)
                    .cornerRadius(20.dp)
            ) {}
            
            Spacer(modifier = GlanceModifier.height(WidgetTheme.spacingSmall))
            
            androidx.glance.text.Text(
                text = "Loading...",
                style = WidgetTypography.bodyMedium
            )
        }
    }
}

/**
 * Error state layout for widgets
 */
@Composable
fun WidgetErrorLayout(
    modifier: GlanceModifier = GlanceModifier,
    message: String = "Unable to load match data",
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusLarge)
            .padding(WidgetTheme.paddingMedium),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            androidx.glance.text.Text(
                text = "⚠️",
                style = TextStyle(fontSize = 24.sp)
            )
            
            Spacer(modifier = GlanceModifier.height(WidgetTheme.spacingSmall))
            
            androidx.glance.text.Text(
                text = message,
                style = WidgetTypography.bodyMedium
            )
        }
    }
}

/**
 * No matches available layout
 */
@Composable
fun WidgetEmptyLayout(
    modifier: GlanceModifier = GlanceModifier,
    message: String = "No live matches",
    backgroundColor: GlanceColorProvider = WidgetColorScheme.background
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(WidgetTheme.radiusLarge)
            .padding(WidgetTheme.paddingMedium),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            androidx.glance.text.Text(
                text = "🏏",
                style = TextStyle(fontSize = 32.sp)
            )
            
            Spacer(modifier = GlanceModifier.height(WidgetTheme.spacingSmall))
            
            androidx.glance.text.Text(
                text = message,
                style = WidgetTypography.bodyMedium
            )
            
            Spacer(modifier = GlanceModifier.height(WidgetTheme.spacingXSmall))
            
            androidx.glance.text.Text(
                text = "Tap to open app",
                style = WidgetTypography.labelSmall
            )
        }
    }
}

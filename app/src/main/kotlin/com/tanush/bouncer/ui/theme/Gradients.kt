package com.tanush.bouncer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import io.sentry.Sentry

// ==========================================
// BOUNCER – Gradient Presets
// Functional gradients only — each serves a
// specific UI purpose. No decorative gradients.
// ==========================================

object GradientPresets {
    
    // ========================================
    // Gold & Premium Gradients
    // ========================================
    
    /**
     * Premium gold shimmer - for hero elements
     */
    fun goldShimmer(alpha: Float = 1f) = Brush.linearGradient(
        colors = listOf(
            GoldLight.copy(alpha = alpha * 0.9f),
            GoldPrimary.copy(alpha = alpha),
            GoldDark.copy(alpha = alpha),
            GoldPrimary.copy(alpha = alpha)
        )
    )
    
    /**
     * Horizontal gold gradient - for buttons and accents
     */
    fun goldHorizontal(alpha: Float = 1f) = Brush.horizontalGradient(
        colors = listOf(
            GoldPrimary.copy(alpha = alpha),
            GoldDark.copy(alpha = alpha),
            GoldDark.copy(alpha = alpha)
        )
    )
    
    /**
     * Subtle gold accent - for backgrounds
     */
    fun goldSubtle(alpha: Float = 0.12f) = Brush.linearGradient(
        colors = listOf(
            GoldDark.copy(alpha = alpha),
            GoldPrimary.copy(alpha = alpha * 0.5f),
            Color.Transparent
        )
    )
    
    /**
     * Radial gold glow - for highlight effects
     */
    fun goldGlow(alpha: Float = 0.25f) = Brush.radialGradient(
        colors = listOf(
            GoldPrimary.copy(alpha = alpha),
            GoldDark.copy(alpha = alpha * 0.5f),
            Color.Transparent
        )
    )
    
    /**
     * Premium metallic gold - for premium badges
     */
    fun metallicGold(alpha: Float = 1f) = Brush.linearGradient(
        colors = listOf(
            GoldLight.copy(alpha = alpha),
            GoldDark.copy(alpha = alpha),
            GoldDark.copy(alpha = alpha),
            GoldDark.copy(alpha = alpha),
            GoldLight.copy(alpha = alpha)
        )
    )
    
    // ========================================
    // Card & Surface Gradients
    // ========================================
    
    /**
     * Premium dark card background
     */
    fun cardBackground() = Brush.linearGradient(
        colors = listOf(
            CardSurface,
            CardDarkBlue,
            CardAlmostBlack
        ),
        start = Offset(0f, 0f),
        end = Offset(1000f, 1000f)
    )
    
    /**
     * Elevated card surface
     */
    fun cardElevated() = Brush.linearGradient(
        colors = listOf(
            CardSurfaceLight,
            CardSurface
        )
    )
    
    /**
     * Dark surface with subtle depth
     */
    fun surfaceGradient() = Brush.verticalGradient(
        colors = listOf(
            BlackElevated,
            BlackBackground
        )
    )
    
    /**
     * Screen background gradient
     */
    fun screenBackground() = Brush.verticalGradient(
        colors = listOf(
            BlackBackground,
            CardDarkBlue.copy(alpha = 0.3f),
            BlackBackground
        )
    )
    
    // ========================================
    // Status Gradients
    // ========================================
    
    /**
     * Success/completed gradient
     */
    fun success(alpha: Float = 1f) = Brush.horizontalGradient(
        colors = listOf(
            SuccessGreen.copy(alpha = alpha),
            SuccessGreenMuted.copy(alpha = alpha * 0.9f)
        )
    )
    
    /**
     * Error/abandoned gradient
     */
    fun error(alpha: Float = 1f) = Brush.horizontalGradient(
        colors = listOf(
            ErrorRed.copy(alpha = alpha),
            ErrorRedMuted.copy(alpha = alpha * 0.9f)
        )
    )
    
    /**
     * Live indicator gradient - premium blue
     */
    fun liveIndicator(alpha: Float = 1f) = Brush.linearGradient(
        colors = listOf(
            LiveBlueBright.copy(alpha = alpha),
            LiveBlue.copy(alpha = alpha)
        )
    )
    
    /**
     * Warning/delayed gradient
     */
    fun warning(alpha: Float = 1f) = Brush.horizontalGradient(
        colors = listOf(
            WarningOrange.copy(alpha = alpha),
            WarningOrangeMuted.copy(alpha = alpha)
        )
    )
    
    // ========================================
    // Effect Gradients
    // ========================================
    
    /**
     * Premium shimmer effect
     */
    fun shimmer(alpha: Float = 0.15f) = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = alpha * 0.5f),
            Color.White.copy(alpha = alpha),
            Color.White.copy(alpha = alpha * 0.5f),
            Color.Transparent
        )
    )
    
    /**
     * Gold shimmer sweep
     */
    fun goldShimmerSweep(alpha: Float = 0.2f) = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            GoldLight.copy(alpha = alpha * 0.5f),
            GoldPrimary.copy(alpha = alpha),
            GoldLight.copy(alpha = alpha * 0.5f),
            Color.Transparent
        )
    )
    
    /**
     * Vertical fade overlay
     */
    fun verticalFade(startAlpha: Float = 0f, endAlpha: Float = 1f) = Brush.verticalGradient(
        colors = listOf(
            BlackBackground.copy(alpha = startAlpha),
            BlackBackground.copy(alpha = endAlpha)
        )
    )
    
    /**
     * Elegant horizontal divider
     */
    fun divider(alpha: Float = 0.08f) = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = alpha),
            Color.White.copy(alpha = alpha),
            Color.Transparent
        )
    )
    
    /**
     * Gold accent divider
     */
    fun goldDivider(alpha: Float = 0.15f) = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            GoldDark.copy(alpha = alpha),
            GoldPrimary.copy(alpha = alpha * 1.2f),
            GoldDark.copy(alpha = alpha),
            Color.Transparent
        )
    )
    
    // ========================================
    // Team / Secondary (30%) Gradients
    // ========================================
    
    /**
     * Team-tinted section header — subtle top-down team wash.
     * Use instead of gold for non-CTA headers and section fills.
     */
    fun teamHeader(teamColor: Color, alpha: Float = 0.18f) = Brush.verticalGradient(
        colors = listOf(
            teamColor.copy(alpha = alpha),
            teamColor.copy(alpha = alpha * 0.4f),
            Color.Transparent
        )
    )
    
    /**
     * Horizontal team gradient for card borders or indicator bars.
     */
    fun teamHorizontal(teamPrimary: Color, teamSecondary: Color, alpha: Float = 0.5f) =
        Brush.horizontalGradient(
            colors = listOf(
                teamPrimary.copy(alpha = alpha),
                teamSecondary.copy(alpha = alpha * 0.7f),
                teamPrimary.copy(alpha = alpha * 0.5f)
            )
        )
    
    /**
     * Subtle team card tint — very faint team wash for card backgrounds.
     */
    fun teamCardTint(teamColor: Color, alpha: Float = 0.06f) = Brush.verticalGradient(
        colors = listOf(
            teamColor.copy(alpha = alpha),
            Color.Transparent
        )
    )
    
    /**
     * Team glow — radial team glow for emphasis spots.
     */
    fun teamRadialGlow(teamColor: Color, alpha: Float = 0.15f) = Brush.radialGradient(
        colors = listOf(
            teamColor.copy(alpha = alpha),
            teamColor.copy(alpha = alpha * 0.3f),
            Color.Transparent
        )
    )
    
    /**
     * Team shimmer sweep — team-colored loading/shimmer effect.
     * Replaces goldShimmerSweep for non-CTA contexts.
     */
    fun teamShimmerSweep(teamColor: Color, alpha: Float = 0.2f) = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            teamColor.copy(alpha = alpha * 0.5f),
            teamColor.copy(alpha = alpha),
            teamColor.copy(alpha = alpha * 0.5f),
            Color.Transparent
        )
    )
    
    /**
     * Team accent divider — replaces goldDivider for non-premium contexts.
     */
    fun teamDivider(teamColor: Color, alpha: Float = 0.15f) = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            teamColor.copy(alpha = alpha * 0.6f),
            teamColor.copy(alpha = alpha),
            teamColor.copy(alpha = alpha * 0.6f),
            Color.Transparent
        )
    )
    
    // ========================================
    // Glassmorphism Effects
    // ========================================
    
    /**
     * Glass morphism overlay - frosted glass
     */
    fun glassMorphism(alpha: Float = 0.1f) = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = alpha * 1.5f),
            Color.White.copy(alpha = alpha * 0.5f),
            Color.White.copy(alpha = alpha * 0.3f)
        )
    )
    
    /**
     * Dark glass effect
     */
    fun darkGlass(alpha: Float = 0.6f) = Brush.linearGradient(
        colors = listOf(
            Color.Black.copy(alpha = alpha),
            Color.Black.copy(alpha = alpha * 0.7f)
        )
    )
    
    /**
     * Premium frosted surface
     */
    fun frostedSurface() = Brush.linearGradient(
        colors = listOf(
            GlassWhite,
            Color.White.copy(alpha = 0.05f)
        )
    )
    
    // ========================================
    // Dynamic Status Gradients
    // ========================================
    
    /**
     * Status badge gradient based on live state
     */
    fun statusBadge(isLive: Boolean, alpha: Float = 1f): Brush {
        try {
            return if (isLive) {
                Brush.linearGradient(
                    colors = listOf(
                        GoldLight.copy(alpha = alpha),
                        GoldDark.copy(alpha = alpha)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        CardSurfaceLight.copy(alpha = alpha),
                        CardSurface.copy(alpha = alpha)
                    )
                )
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Team glow effect
     */
    fun teamGlow(color: Color, alpha: Float = 0.15f) = Brush.radialGradient(
        colors = listOf(
            color.copy(alpha = alpha),
            color.copy(alpha = alpha * 0.3f),
            Color.Transparent
        )
    )
    
    /**
     * Score highlight gradient
     */
    fun scoreHighlight(isWinning: Boolean) = if (isWinning) {
        try {
            Brush.linearGradient(
                colors = listOf(
                    GoldPrimary,
                    GoldDark
                )
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    } else {
        Brush.linearGradient(
            colors = listOf(
                TextSecondary,
                TextTertiary
            )
        )
    }
}

// =============================================================================
// Top-level Gradient Functions for Legacy Compatibility
// =============================================================================

/**
 * Hero gradient for premium hero sections.
 */
fun heroGradient() = Brush.verticalGradient(
    colors = listOf(
        BlackElevated,
        CardDarkBlue.copy(alpha = 0.6f),
        BlackBackground
    )
)

/**
 * Surface gradient for content backgrounds.
 */
fun surfaceGradient() = GradientPresets.surfaceGradient()

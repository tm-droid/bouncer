package com.tanush.bouncer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.tanush.bouncer.ui.accessibility.ReducedMotionDetector
import com.tanush.bouncer.ui.accessibility.LocalAccessibilityPreferences

// ==========================================
// BOUNCER – Design Token System v2.0: Motion
// Design Spec: android/design/BOUNCER_DESIGN_SYSTEM.md
//
// Timing Functions:
//   ease-out-expo      — primary motion, reveals
//   ease-in-out-quint  — dialogs, modals
//   ease-out-back      — celebratory bounces
//   spring-gentle      — general spring (d:15, s:120)
//   spring-bouncy      — celebration (d:10, s:200)
//
// Duration Scale:
//   instant  50ms   — immediate feedback
//   micro    100ms  — micro-interactions
//   fast     150ms  — chip selection, toggles
//   normal   250ms  — standard transitions
//   moderate 350ms  — screen transitions
//   slow     500ms  — complex animations
//   emphasis 700ms  — celebrations
// ==========================================

/**
 * Easing curves from the design spec
 */
object BouncerEasing {
    // Primary motion — reveals, enters
    val EaseOutExpo      = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    // Dialogs, modals
    val EaseInOutQuint   = CubicBezierEasing(0.83f, 0f, 0.17f, 1f)
    // Celebratory bounces
    val EaseOutBack      = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
}

// Backward-compatible alias
object MaterialEasing {
    val Standard           = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val StandardAccelerate = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
    val StandardDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

    val Emphasized           = BouncerEasing.EaseOutExpo
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    val FastOutSlowIn    = Standard
    val LinearOutSlowIn  = StandardDecelerate
    val FastOutLinearIn  = StandardAccelerate
}

/**
 * Duration tokens — 7-tier scale from design spec.
 */
object AnimationDuration {
    const val instant  = 50     // Immediate feedback
    const val micro    = 100    // Micro-interactions, hover
    const val fast     = 150    // Chip selection, toggles
    const val normal   = 250    // Standard transitions
    const val moderate = 350    // Screen transitions
    const val slow     = 500    // Complex animations
    const val emphasis = 700    // Celebration moments

    // Specific animation durations from spec
    const val cardTap     = 100   // Card tap scale
    const val screenEnter = 350   // Screen enter
    const val screenExit  = 250   // Screen exit
    const val tabSwitch   = 250   // Tab switch
    const val sheetEnter  = 350   // Bottom sheet enter
    const val scoreUpdate = 300   // Score update (spring)
    const val confetti    = 1500  // Celebration confetti
    const val progressFill  = 600 // Progress fill

    // Ambient / looping durations
    const val shimmer = 1500
    const val pulse   = 2000
    const val ambient = 3000

    // Page transitions (aliases)
    const val pageEnter = screenEnter
    const val pageExit  = screenExit

    // Legacy aliases
    @Deprecated("Use AnimationDuration.micro") const val veryFast = micro
    @Deprecated("Use AnimationDuration.normal") const val quick    = normal
    @Deprecated("Use AnimationDuration.slow") const val verySlow  = slow
    @Deprecated("Use AnimationDuration.slow") const val extraSlow = slow
    @Deprecated("Use AnimationDuration.ambient") const val rotate  = ambient
}

/**
 * Stagger delays for list item animations.
 */
object AnimationDelay {
    const val none    = 0
    const val stagger = 50     // Per-item stagger (design spec: 50ms)
    const val short   = 80
    const val medium  = 200
    const val long    = 300

    @Deprecated("Use AnimationDelay.stagger") const val micro = stagger
    @Deprecated("Use AnimationDelay.medium") const val veryLong = medium
}

/**
 * Press feedback scale — subtle, tied to user intent.
 */
object PressScale {
    const val subtle      = 0.985f   // Light tap feedback
    const val normal      = 0.98f    // Card press (design spec)
    const val pronounced  = 0.95f    // CTA press

    @Deprecated("Use PressScale.pronounced") const val bouncy = pronounced
}

/**
 * Pulse animation scale range — for live indicators only.
 */
object PulseScale {
    const val min = 0.97f
    const val max = 1.03f

    @Deprecated("Use PulseScale.min") const val minSubtle = min
    @Deprecated("Use PulseScale.max") const val maxSubtle = max
    @Deprecated("Use PulseScale.min") const val minNormal = min
    @Deprecated("Use PulseScale.max") const val maxNormal = max
    @Deprecated("Use PulseScale.min") const val minPronounced = min
    @Deprecated("Use PulseScale.max") const val maxPronounced = max
    @Deprecated("Use PulseScale.min") const val minLive = min
    @Deprecated("Use PulseScale.max") const val maxLive = max
}

/**
 * Opacity tokens for fade transitions.
 */
object AnimationAlpha {
    const val transparent = 0f
    const val faint       = 0.2f
    const val medium      = 0.5f
    const val strong      = 0.8f
    const val opaque      = 1f

    @Deprecated("Use AnimationAlpha.faint") const val light = faint
    @Deprecated("Use AnimationAlpha.strong") const val nearOpaque = strong
}

/**
 * Spring configuration from design spec.
 */
object SpringConfig {
    // Gentle spring — general motion (damping: 15, stiffness: 120)
    const val gentleDamping   = 15f
    const val gentleStiffness = 120f

    // Bouncy spring — celebrations (damping: 10, stiffness: 200)
    const val bouncyDamping   = 10f
    const val bouncyStiffness = 200f

    // Simplified aliases
    const val noBounce    = 1f
    const val lowBounce   = 0.75f
    const val medBounce   = 0.6f
    const val low    = gentleStiffness
    const val medium = 400f
    const val high   = 800f

    // Legacy aliases
    @Deprecated("Use SpringConfig.noBounce") const val dampingNoBounce = noBounce
    @Deprecated("Use SpringConfig.lowBounce") const val dampingLowBounce = lowBounce
    @Deprecated("Use SpringConfig.medBounce") const val dampingMediumBounce = medBounce
    @Deprecated("Removed — too aggressive") const val dampingHighBounce = medBounce
    @Deprecated("Use SpringConfig.low") const val stiffnessVeryLow = low
    @Deprecated("Use SpringConfig.low") const val stiffnessLow = low
    @Deprecated("Use SpringConfig.medium") const val stiffnessMedium = medium
    @Deprecated("Use SpringConfig.high") const val stiffnessHigh = high
    @Deprecated("Use SpringConfig.high") const val stiffnessVeryHigh = high
}

// ==========================================
// Motion Accessibility Integration
// Bridges animation tokens with system reduced-motion preferences
// and the accessibility layer in InteractionAccessibility.kt.
// ==========================================

/**
 * Motion scale provider — reads system + accessibility animation scale
 * and exposes it via [LocalMotionScale] for the entire subtree.
 */
object BouncerMotionScale {
    /** CompositionLocal carrying the effective motion scale (0f–1f+). */
    val LocalMotionScale = compositionLocalOf { 1.0f }

    /**
     * Returns [baseDuration] multiplied by the current motion scale.
     * When the scale is 0 the result is 0 (instant).
     */
    @Composable
    fun scaledDuration(baseDuration: Int): Int {
        val scale = LocalMotionScale.current
        return (baseDuration * scale).toInt()
    }

    /**
     * True when motion should be skipped entirely — either because the
     * scale is 0 **or** the accessibility layer reports reduced motion.
     */
    @Composable
    fun isReducedMotion(): Boolean {
        val scale = LocalMotionScale.current
        val prefs = LocalAccessibilityPreferences.current
        return scale == 0f || prefs.reducedMotionEnabled
    }
}

/**
 * Provides [BouncerMotionScale.LocalMotionScale] to [content] by combining
 * the system animator-duration scale with the accessibility-layer
 * [LocalAccessibilityPreferences.animationScale].
 *
 * If the system animation scale is 0 the value is hard-locked to 0f.
 */
@Composable
fun MotionScaleProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemScale = remember { ReducedMotionDetector.getAnimationScale(context) }
    val accessibilityScale = LocalAccessibilityPreferences.current.animationScale

    val finalScale = if (systemScale == 0f) 0f else systemScale * accessibilityScale

    CompositionLocalProvider(
        BouncerMotionScale.LocalMotionScale provides finalScale,
        content = content
    )
}

/**
 * Reduced-motion duration tokens — mirrors [AnimationDuration] but with
 * instant (0 ms) values. Used as the fallback when reduced motion is on.
 */
object ReducedMotionDuration {
    const val instant  = 50    // Minimum tactile feedback preserved
    const val micro    = 0
    const val fast     = 0
    const val normal   = 0
    const val moderate = 0
    const val slow     = 0
    const val emphasis = 0

    const val cardTap       = 0
    const val screenEnter   = 0
    const val screenExit    = 0
    const val tabSwitch     = 0
    const val sheetEnter    = 0
    const val scoreUpdate   = 0
    const val confetti      = 0
    const val progressFill  = 0

    const val shimmer = 0
    const val pulse   = 0
    const val ambient = 0
}

/**
 * Returns an accessibility-aware duration for the given [base] value.
 *
 * - Reduced motion → 0 (skip animation).
 * - Otherwise → [base] × current motion scale.
 */
@Composable
fun animationDuration(base: Int): Int {
    if (BouncerMotionScale.isReducedMotion()) return 0
    return (base * BouncerMotionScale.LocalMotionScale.current).toInt()
}

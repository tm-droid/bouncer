package com.tanush.bouncer.ui.accessibility

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Interaction Accessibility Utilities for Bouncer Cricket App
 * 
 * Provides large touch targets, simplified navigation, switch control support,
 * voice command integration, haptic feedback, and motion preferences.
 */

// ==========================================
// Large Touch Target Mode
// ==========================================

/**
 * Large touch target mode wrapper that expands all touch targets
 */
@Composable
fun LargeTouchTargetMode(
    enabled: Boolean = true,
    minimumSize: Dp = 56.dp,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalLargeTouchTargetEnabled provides enabled,
        LocalMinimumTouchSize provides minimumSize
    ) {
        content()
    }
}

/**
 * Local composition for large touch target mode
 */
val LocalLargeTouchTargetEnabled = compositionLocalOf { false }
val LocalMinimumTouchSize = compositionLocalOf { 48.dp }

/**
 * Modifier that automatically expands touch target when large mode is enabled
 */
fun Modifier.adaptiveTouchTarget(): Modifier = composed {
    val largeModeEnabled = LocalLargeTouchTargetEnabled.current
    val minSize = LocalMinimumTouchSize.current
    
    if (largeModeEnabled) {
        this.sizeIn(minWidth = minSize, minHeight = minSize)
    } else {
        this.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    }
}

/**
 * Modifier that adds extra padding for touch targets
 */
fun Modifier.expandedTouchPadding(
    horizontal: Dp = 8.dp,
    vertical: Dp = 8.dp
): Modifier = this.padding(horizontal = horizontal, vertical = vertical)

// ==========================================
// Simplified Navigation Mode
// ==========================================

/**
 * Simplified navigation preferences
 */
data class SimplifiedNavigationPreferences(
    val enabled: Boolean = false,
    val reducedOptions: Boolean = true,
    val linearNavigation: Boolean = true,
    val skipDecorative: Boolean = true,
    val confirmActions: Boolean = true,
    val largerTouchTargets: Boolean = true
)

/**
 * Local composition for simplified navigation
 */
val LocalSimplifiedNavigation = compositionLocalOf { SimplifiedNavigationPreferences() }

/**
 * Simplified navigation mode provider
 */
@Composable
fun SimplifiedNavigationMode(
    preferences: SimplifiedNavigationPreferences,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalSimplifiedNavigation provides preferences
    ) {
        content()
    }
}

/**
 * Modifier that hides element in simplified mode
 */
@Composable
fun Modifier.hideInSimplifiedMode(): Modifier {
    val simplified = LocalSimplifiedNavigation.current
    return if (simplified.enabled && simplified.reducedOptions) {
        this.size(0.dp)
    } else {
        this
    }
}

/**
 * Composable that only renders content outside simplified mode
 */
@Composable
fun NonEssentialContent(
    content: @Composable () -> Unit
) {
    val simplified = LocalSimplifiedNavigation.current
    if (!simplified.enabled || !simplified.reducedOptions) {
        content()
    }
}

/**
 * Composable that only renders essential content (always shown)
 */
@Composable
fun EssentialContent(
    content: @Composable () -> Unit
) {
    content()
}

// ==========================================
// Switch Control Support
// ==========================================

/**
 * Switch control support utilities for external switch access devices
 */
object SwitchControlSupport {
    
    /**
     * Check if switch access is enabled on device
     */
    fun isSwitchAccessEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return accessibilityManager?.isEnabled == true && 
               accessibilityManager.isTouchExplorationEnabled
    }
    
    /**
     * Get recommended focus order for switch navigation
     */
    fun getRecommendedFocusOrder(): List<FocusableElement> {
        return listOf(
            FocusableElement.NAVIGATION,
            FocusableElement.PRIMARY_CONTENT,
            FocusableElement.ACTIONS,
            FocusableElement.SECONDARY_CONTENT
        )
    }
    
    /**
     * Focus element categories for switch navigation
     */
    enum class FocusableElement {
        NAVIGATION,
        PRIMARY_CONTENT,
        ACTIONS,
        SECONDARY_CONTENT,
        DECORATIVE
    }
}

/**
 * Modifier for switch-control friendly focus
 */
fun Modifier.switchControlFriendly(
    focusCategory: SwitchControlSupport.FocusableElement
): Modifier = composed {
    val context = LocalContext.current
    val isSwitchMode = remember { SwitchControlSupport.isSwitchAccessEnabled(context) }
    
    if (isSwitchMode && focusCategory == SwitchControlSupport.FocusableElement.DECORATIVE) {
        this.size(0.dp) // Hide decorative elements in switch mode
    } else {
        this
    }
}

// ==========================================
// Voice Command Actions
// ==========================================

/**
 * Voice command action definitions for cricket app
 */
object VoiceCommandActions {
    
    /**
     * Voice command data class
     */
    data class VoiceCommand(
        val phrases: List<String>,
        val action: () -> Unit,
        val description: String
    )
    
    /**
     * Common voice commands for cricket navigation
     */
    fun createNavigationCommands(
        onGoHome: () -> Unit,
        onRefresh: () -> Unit,
        onBack: () -> Unit,
        onNextMatch: () -> Unit,
        onPreviousMatch: () -> Unit
    ): List<VoiceCommand> = listOf(
        VoiceCommand(
            phrases = listOf("go home", "home screen", "main screen"),
            action = onGoHome,
            description = "Navigate to home screen"
        ),
        VoiceCommand(
            phrases = listOf("refresh", "update", "reload"),
            action = onRefresh,
            description = "Refresh current content"
        ),
        VoiceCommand(
            phrases = listOf("go back", "back", "previous screen"),
            action = onBack,
            description = "Go to previous screen"
        ),
        VoiceCommand(
            phrases = listOf("next match", "show next"),
            action = onNextMatch,
            description = "Show next match"
        ),
        VoiceCommand(
            phrases = listOf("previous match", "show previous"),
            action = onPreviousMatch,
            description = "Show previous match"
        )
    )
    
    /**
     * Cricket-specific voice commands
     */
    fun createCricketCommands(
        onShowScorecard: () -> Unit,
        onShowLive: () -> Unit,
        onShowStats: () -> Unit,
        onShowProbability: () -> Unit,
        onReadScore: () -> Unit
    ): List<VoiceCommand> = listOf(
        VoiceCommand(
            phrases = listOf("show scorecard", "scorecard", "full scorecard"),
            action = onShowScorecard,
            description = "Show full scorecard"
        ),
        VoiceCommand(
            phrases = listOf("show live", "live matches", "what's live"),
            action = onShowLive,
            description = "Show live matches"
        ),
        VoiceCommand(
            phrases = listOf("show stats", "statistics", "player stats"),
            action = onShowStats,
            description = "Show statistics"
        ),
        VoiceCommand(
            phrases = listOf("win probability", "who's winning", "probability"),
            action = onShowProbability,
            description = "Show win probability"
        ),
        VoiceCommand(
            phrases = listOf("read score", "what's the score", "current score"),
            action = onReadScore,
            description = "Read current score aloud"
        )
    )
    
    /**
     * Match a spoken phrase to a voice command
     */
    fun matchCommand(
        spokenPhrase: String,
        commands: List<VoiceCommand>
    ): VoiceCommand? {
        val normalized = spokenPhrase.lowercase().trim()
        return commands.find { command ->
            command.phrases.any { phrase ->
                normalized.contains(phrase) || phrase.contains(normalized)
            }
        }
    }
}

// ==========================================
// Haptic Feedback Patterns
// ==========================================

/**
 * Sealed class representing haptic feedback patterns
 */
sealed class HapticPattern {
    /**
     * Success feedback - used for positive actions like successful refresh
     */
    data object Success : HapticPattern()
    
    /**
     * Error feedback - used for errors or failed actions
     */
    data object Error : HapticPattern()
    
    /**
     * Boundary feedback - used when user reaches navigation boundaries
     */
    data object Boundary : HapticPattern()
    
    /**
     * Wicket feedback - special pattern for wicket events
     */
    data object Wicket : HapticPattern()
    
    /**
     * Boundary hit (4 or 6) feedback
     */
    data class BoundaryHit(val isSix: Boolean) : HapticPattern()
    
    /**
     * Live update tick
     */
    data object LiveTick : HapticPattern()
    
    /**
     * Selection feedback
     */
    data object Selection : HapticPattern()
    
    /**
     * Custom pattern with specified timings
     */
    data class Custom(
        val timings: LongArray,
        val amplitudes: IntArray
    ) : HapticPattern() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Custom
            return timings.contentEquals(other.timings) && amplitudes.contentEquals(other.amplitudes)
        }
        
        override fun hashCode(): Int {
            var result = timings.contentHashCode()
            result = 31 * result + amplitudes.contentHashCode()
            return result
        }
    }
}

/**
 * Haptic feedback controller
 */
class HapticController(private val context: Context) {
    
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    
    /**
     * Check if haptic feedback is available
     */
    fun isHapticAvailable(): Boolean {
        return vibrator?.hasVibrator() == true
    }
    
    /**
     * Execute a haptic pattern
     */
    fun execute(pattern: HapticPattern) {
        if (!isHapticAvailable()) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (pattern) {
                is HapticPattern.Success -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                is HapticPattern.Error -> VibrationEffect.createWaveform(
                    longArrayOf(0, 100, 50, 100),
                    intArrayOf(0, 200, 0, 200),
                    -1
                )
                is HapticPattern.Boundary -> VibrationEffect.createOneShot(30, 150)
                is HapticPattern.Wicket -> VibrationEffect.createWaveform(
                    longArrayOf(0, 150, 50, 150, 50, 200),
                    intArrayOf(0, 255, 0, 255, 0, 255),
                    -1
                )
                is HapticPattern.BoundaryHit -> {
                    if (pattern.isSix) {
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 100, 30, 100, 30, 100),
                            intArrayOf(0, 200, 0, 220, 0, 255),
                            -1
                        )
                    } else {
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 80, 40, 80),
                            intArrayOf(0, 180, 0, 200),
                            -1
                        )
                    }
                }
                is HapticPattern.LiveTick -> VibrationEffect.createOneShot(10, 100)
                is HapticPattern.Selection -> VibrationEffect.createOneShot(20, 120)
                is HapticPattern.Custom -> VibrationEffect.createWaveform(
                    pattern.timings,
                    pattern.amplitudes,
                    -1
                )
            }
            vibrator?.vibrate(effect)
        } else {
            // Fallback for older devices
            @Suppress("DEPRECATION")
            when (pattern) {
                is HapticPattern.Success -> vibrator?.vibrate(50)
                is HapticPattern.Error -> vibrator?.vibrate(longArrayOf(0, 100, 50, 100), -1)
                is HapticPattern.Boundary -> vibrator?.vibrate(30)
                is HapticPattern.Wicket -> vibrator?.vibrate(longArrayOf(0, 150, 50, 150, 50, 200), -1)
                is HapticPattern.BoundaryHit -> vibrator?.vibrate(longArrayOf(0, 80, 40, 80), -1)
                is HapticPattern.LiveTick -> vibrator?.vibrate(10)
                is HapticPattern.Selection -> vibrator?.vibrate(20)
                is HapticPattern.Custom -> vibrator?.vibrate(pattern.timings, -1)
            }
        }
    }
}

/**
 * Composable to get haptic controller
 */
@Composable
fun rememberHapticController(): HapticController {
    val context = LocalContext.current
    return remember { HapticController(context) }
}

/**
 * Local composition for haptic controller
 */
val LocalHapticController = compositionLocalOf<HapticController?> { null }

// ==========================================
// Accessibility Preferences
// ==========================================

/**
 * Comprehensive accessibility preferences
 */
data class AccessibilityPreferences(
    // Touch & Interaction
    val largeTouchTargetsEnabled: Boolean = false,
    val minimumTouchSize: Dp = 48.dp,
    
    // Navigation
    val simplifiedNavigationEnabled: Boolean = false,
    val linearNavigationEnabled: Boolean = false,
    val confirmActionsEnabled: Boolean = false,
    
    // Visual
    val colorBlindMode: ColorBlindMode = ColorBlindMode.NONE,
    val highContrastEnabled: Boolean = false,
    val largeTextEnabled: Boolean = false,
    val boldTextEnabled: Boolean = false,
    
    // Motion
    val reducedMotionEnabled: Boolean = false,
    val animationScale: Float = 1.0f,
    
    // Haptic
    val hapticFeedbackEnabled: Boolean = true,
    
    // Audio
    val screenReaderOptimized: Boolean = false,
    val liveAnnouncementsEnabled: Boolean = true
)

/**
 * Local composition for accessibility preferences
 */
val LocalAccessibilityPreferences = compositionLocalOf { AccessibilityPreferences() }

/**
 * Accessibility preferences provider
 */
@Composable
fun AccessibilityPreferencesProvider(
    preferences: AccessibilityPreferences,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val hapticController = remember { HapticController(context) }
    
    CompositionLocalProvider(
        LocalAccessibilityPreferences provides preferences,
        LocalHapticController provides hapticController,
        LocalLargeTouchTargetEnabled provides preferences.largeTouchTargetsEnabled,
        LocalMinimumTouchSize provides preferences.minimumTouchSize
    ) {
        content()
    }
}

// ==========================================
// Reduced Motion Detection
// ==========================================

/**
 * Reduced motion detector for respecting system preferences
 */
object ReducedMotionDetector {
    
    /**
     * Check if user has enabled reduced motion in system settings
     */
    fun isReducedMotionEnabled(context: Context): Boolean {
        return try {
            val animatorDurationScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            animatorDurationScale == 0f
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get the animation duration scale from system settings
     */
    fun getAnimationScale(context: Context): Float {
        return try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
        } catch (e: Exception) {
            1.0f
        }
    }
    
    /**
     * Calculate adjusted animation duration based on system preferences
     */
    fun adjustedDuration(baseMillis: Long, context: Context): Long {
        val scale = getAnimationScale(context)
        return if (scale == 0f) 0L else (baseMillis * scale).toLong()
    }
}

/**
 * Composable to observe reduced motion preference
 */
@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember { ReducedMotionDetector.isReducedMotionEnabled(context) }
}

/**
 * Composable to get adjusted animation duration
 */
@Composable
fun rememberAdjustedAnimationDuration(baseMillis: Long): Long {
    val context = LocalContext.current
    return remember { ReducedMotionDetector.adjustedDuration(baseMillis, context) }
}

/**
 * Modifier that respects reduced motion preference
 */
@Composable
fun Modifier.respectReducedMotion(
    animatedModifier: @Composable Modifier.() -> Modifier,
    staticModifier: @Composable Modifier.() -> Modifier = { this }
): Modifier {
    val reducedMotion = rememberReducedMotionEnabled()
    val prefs = LocalAccessibilityPreferences.current
    
    return if (reducedMotion || prefs.reducedMotionEnabled) {
        this.staticModifier()
    } else {
        this.animatedModifier()
    }
}

// ==========================================
// Accessibility State
// ==========================================

/**
 * Holder for runtime accessibility state
 */
class AccessibilityState(
    private val context: Context
) {
    /**
     * Check if TalkBack or other screen reader is active
     */
    fun isScreenReaderActive(): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return accessibilityManager?.isTouchExplorationEnabled == true
    }
    
    /**
     * Check if switch access is enabled
     */
    fun isSwitchAccessEnabled(): Boolean {
        return SwitchControlSupport.isSwitchAccessEnabled(context)
    }
    
    /**
     * Check if any accessibility service is active
     */
    fun isAnyAccessibilityServiceActive(): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return accessibilityManager?.isEnabled == true
    }
    
    /**
     * Get recommended accessibility preferences based on system state
     */
    fun getRecommendedPreferences(): AccessibilityPreferences {
        return AccessibilityPreferences(
            largeTouchTargetsEnabled = isScreenReaderActive() || isSwitchAccessEnabled(),
            screenReaderOptimized = isScreenReaderActive(),
            reducedMotionEnabled = ReducedMotionDetector.isReducedMotionEnabled(context),
            animationScale = ReducedMotionDetector.getAnimationScale(context),
            hapticFeedbackEnabled = !isScreenReaderActive() // Often disabled with screen readers
        )
    }
}

/**
 * Remember accessibility state
 */
@Composable
fun rememberAccessibilityState(): AccessibilityState {
    val context = LocalContext.current
    return remember { AccessibilityState(context) }
}

package com.tanush.bouncer.managers

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * HapticFeedbackManager - Immersive Match Event Feedback System
 * 
 * Provides distinctive haptic patterns for different cricket events:
 * - Wickets: Strong double vibration (celebration/tension)
 * - Sixes: Triple burst (excitement)
 * - Fours: Single medium pulse
 * - Milestones: Long gentle wave (achievement)
 * - UI interactions: Subtle clicks
 */
class HapticFeedbackManager(private val context: Context) {
    
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    companion object {
        // Timing patterns (in milliseconds)
        private val PATTERN_WICKET = longArrayOf(0, 80, 50, 120, 50, 80) // Strong double pulse
        private val PATTERN_SIX = longArrayOf(0, 60, 40, 60, 40, 60, 40, 100) // Triple burst + climax
        private val PATTERN_FOUR = longArrayOf(0, 50, 30, 70) // Single bump + tail
        private val PATTERN_MILESTONE = longArrayOf(0, 100, 50, 100, 50, 200) // Wave pattern
        private val PATTERN_WIN = longArrayOf(0, 100, 80, 100, 80, 100, 80, 300) // Victory crescendo
        private val PATTERN_CATCH = longArrayOf(0, 40, 20, 60) // Quick tension
        private val PATTERN_RUNOUT = longArrayOf(0, 30, 20, 30, 20, 80) // Rapid succession
        
        // Amplitude patterns (0-255, API 26+)
        private val AMPLITUDES_WICKET = intArrayOf(0, 200, 0, 255, 0, 200)
        private val AMPLITUDES_SIX = intArrayOf(0, 180, 0, 180, 0, 180, 0, 255)
        private val AMPLITUDES_FOUR = intArrayOf(0, 150, 0, 200)
        private val AMPLITUDES_MILESTONE = intArrayOf(0, 100, 0, 150, 0, 200)
        private val AMPLITUDES_WIN = intArrayOf(0, 150, 0, 180, 0, 200, 0, 255)
        private val AMPLITUDES_CATCH = intArrayOf(0, 180, 0, 220)
        private val AMPLITUDES_RUNOUT = intArrayOf(0, 150, 0, 180, 0, 255)
    }
    
    /**
     * Check if haptic feedback is available
     */
    fun isHapticAvailable(): Boolean {
        return vibrator.hasVibrator()
    }
    
    /**
     * Check if amplitude control is supported (API 26+)
     */
    fun isAmplitudeControlAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()
    }
    
    // ==========================================
    // CRICKET EVENT HAPTICS
    // ==========================================
    
    /**
     * Wicket taken - Strong double vibration
     */
    fun playWicketHaptic() {
        playPattern(PATTERN_WICKET, AMPLITUDES_WICKET)
    }
    
    /**
     * Six hit - Exciting triple burst
     */
    fun playSixHaptic() {
        playPattern(PATTERN_SIX, AMPLITUDES_SIX)
    }
    
    /**
     * Four hit - Medium single pulse
     */
    fun playFourHaptic() {
        playPattern(PATTERN_FOUR, AMPLITUDES_FOUR)
    }
    
    /**
     * Milestone achieved (50, 100, 5-wicket haul)
     */
    fun playMilestoneHaptic() {
        playPattern(PATTERN_MILESTONE, AMPLITUDES_MILESTONE)
    }
    
    /**
     * Match/Innings won
     */
    fun playWinHaptic() {
        playPattern(PATTERN_WIN, AMPLITUDES_WIN)
    }
    
    /**
     * Catch taken
     */
    fun playCatchHaptic() {
        playPattern(PATTERN_CATCH, AMPLITUDES_CATCH)
    }
    
    /**
     * Run out
     */
    fun playRunOutHaptic() {
        playPattern(PATTERN_RUNOUT, AMPLITUDES_RUNOUT)
    }
    
    // ==========================================
    // UI INTERACTION HAPTICS
    // ==========================================
    
    /**
     * Light click for button presses
     */
    fun playClickHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            playSimpleVibration(20)
        }
    }
    
    /**
     * Heavy click for confirmations
     */
    fun playHeavyClickHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            playSimpleVibration(40)
        }
    }
    
    /**
     * Double click for selections
     */
    fun playDoubleClickHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            playPattern(longArrayOf(0, 20, 50, 20), null)
        }
    }
    
    /**
     * Tick for scrolling/swipe gestures
     */
    fun playTickHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            playSimpleVibration(10)
        }
    }
    
    /**
     * Error feedback
     */
    fun playErrorHaptic() {
        playPattern(longArrayOf(0, 50, 30, 50, 30, 100), intArrayOf(0, 200, 0, 200, 0, 150))
    }
    
    /**
     * Success feedback
     */
    fun playSuccessHaptic() {
        playPattern(longArrayOf(0, 30, 50, 80), intArrayOf(0, 150, 0, 220))
    }
    
    // ==========================================
    // SPECIAL EFFECTS
    // ==========================================
    
    /**
     * Win probability shift (gentle feedback for probability changes)
     */
    fun playProbabilityShiftHaptic(magnitude: Float) {
        val intensity = (magnitude.coerceIn(0f, 1f) * 150 + 50).toInt()
        val duration = (magnitude.coerceIn(0f, 1f) * 50 + 20).toLong()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, intensity))
        } else {
            playSimpleVibration(duration)
        }
    }
    
    /**
     * Pulse effect for live match updates
     */
    fun playLivePulseHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, 80))
        } else {
            playSimpleVibration(30)
        }
    }
    
    /**
     * Cancel any ongoing vibration
     */
    fun cancel() {
        vibrator.cancel()
    }
    
    // ==========================================
    // INTERNAL HELPERS
    // ==========================================
    
    private fun playPattern(pattern: LongArray, amplitudes: IntArray?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (amplitudes != null && vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
    
    private fun playSimpleVibration(durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }
}

/**
 * Composable helper to get HapticFeedbackManager
 */
@Composable
fun rememberHapticFeedbackManager(): HapticFeedbackManager {
    val context = LocalContext.current
    return remember { HapticFeedbackManager(context) }
}

/**
 * Extension to trigger haptics from a View
 */
fun View.performCricketHaptic(event: CricketHapticEvent) {
    val feedbackConstant = when (event) {
        CricketHapticEvent.WICKET -> HapticFeedbackConstants.LONG_PRESS
        CricketHapticEvent.SIX -> HapticFeedbackConstants.VIRTUAL_KEY
        CricketHapticEvent.FOUR -> HapticFeedbackConstants.KEYBOARD_TAP
        CricketHapticEvent.MILESTONE -> HapticFeedbackConstants.LONG_PRESS
        CricketHapticEvent.CLICK -> HapticFeedbackConstants.KEYBOARD_TAP
    }
    performHapticFeedback(feedbackConstant)
}

/**
 * Cricket-specific haptic events
 */
enum class CricketHapticEvent {
    WICKET,
    SIX,
    FOUR,
    MILESTONE,
    CLICK
}

/**
 * Extension for Compose HapticFeedback to add cricket events
 */
fun HapticFeedback.performCricket(event: CricketHapticEvent) {
    val type = when (event) {
        CricketHapticEvent.WICKET, 
        CricketHapticEvent.SIX,
        CricketHapticEvent.MILESTONE -> HapticFeedbackType.LongPress
        CricketHapticEvent.FOUR,
        CricketHapticEvent.CLICK -> HapticFeedbackType.TextHandleMove
    }
    performHapticFeedback(type)
}

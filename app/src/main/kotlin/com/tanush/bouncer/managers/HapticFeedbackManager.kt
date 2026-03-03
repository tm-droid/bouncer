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
import io.sentry.Sentry

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
        try {
            return vibrator.hasVibrator()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Check if amplitude control is supported (API 26+)
     */
    fun isAmplitudeControlAvailable(): Boolean {
        try {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    // ==========================================
    // CRICKET EVENT HAPTICS
    // ==========================================
    
    /**
     * Wicket taken - Strong double vibration
     */
    fun playWicketHaptic() {
        try {
            playPattern(PATTERN_WICKET, AMPLITUDES_WICKET)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Six hit - Exciting triple burst
     */
    fun playSixHaptic() {
        try {
            playPattern(PATTERN_SIX, AMPLITUDES_SIX)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Four hit - Medium single pulse
     */
    fun playFourHaptic() {
        try {
            playPattern(PATTERN_FOUR, AMPLITUDES_FOUR)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Milestone achieved (50, 100, 5-wicket haul)
     */
    fun playMilestoneHaptic() {
        try {
            playPattern(PATTERN_MILESTONE, AMPLITUDES_MILESTONE)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Match/Innings won
     */
    fun playWinHaptic() {
        try {
            playPattern(PATTERN_WIN, AMPLITUDES_WIN)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Catch taken
     */
    fun playCatchHaptic() {
        try {
            playPattern(PATTERN_CATCH, AMPLITUDES_CATCH)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Run out
     */
    fun playRunOutHaptic() {
        try {
            playPattern(PATTERN_RUNOUT, AMPLITUDES_RUNOUT)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    // ==========================================
    // UI INTERACTION HAPTICS
    // ==========================================
    
    /**
     * Light click for button presses
     */
    fun playClickHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                playSimpleVibration(20)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Heavy click for confirmations
     */
    fun playHeavyClickHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                playSimpleVibration(40)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Double click for selections
     */
    fun playDoubleClickHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                playPattern(longArrayOf(0, 20, 50, 20), null)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Tick for scrolling/swipe gestures
     */
    fun playTickHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                playSimpleVibration(10)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Error feedback
     */
    fun playErrorHaptic() {
        try {
            playPattern(longArrayOf(0, 50, 30, 50, 30, 100), intArrayOf(0, 200, 0, 200, 0, 150))
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Success feedback
     */
    fun playSuccessHaptic() {
        try {
            playPattern(longArrayOf(0, 30, 50, 80), intArrayOf(0, 150, 0, 220))
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    // ==========================================
    // SPECIAL EFFECTS
    // ==========================================
    
    /**
     * Win probability shift (gentle feedback for probability changes)
     */
    fun playProbabilityShiftHaptic(magnitude: Float) {
        try {
            val intensity = (magnitude.coerceIn(0f, 1f) * 150 + 50).toInt()
            val duration = (magnitude.coerceIn(0f, 1f) * 50 + 20).toLong()
        
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, intensity))
            } else {
                playSimpleVibration(duration)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Pulse effect for live match updates
     */
    fun playLivePulseHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, 80))
            } else {
                playSimpleVibration(30)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Cancel any ongoing vibration
     */
    fun cancel() {
        try {
            vibrator.cancel()
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    // ==========================================
    // INTERNAL HELPERS
    // ==========================================
    
    private fun playPattern(pattern: LongArray, amplitudes: IntArray?) {
        try {
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
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    private fun playSimpleVibration(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
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
    try {
        val feedbackConstant = when (event) {
            CricketHapticEvent.WICKET -> HapticFeedbackConstants.LONG_PRESS
            CricketHapticEvent.SIX -> HapticFeedbackConstants.VIRTUAL_KEY
            CricketHapticEvent.FOUR -> HapticFeedbackConstants.KEYBOARD_TAP
            CricketHapticEvent.MILESTONE -> HapticFeedbackConstants.LONG_PRESS
            CricketHapticEvent.CLICK -> HapticFeedbackConstants.KEYBOARD_TAP
        }
        performHapticFeedback(feedbackConstant)
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
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
    try {
        val type = when (event) {
            CricketHapticEvent.WICKET, 
            CricketHapticEvent.SIX,
            CricketHapticEvent.MILESTONE -> HapticFeedbackType.LongPress
            CricketHapticEvent.FOUR,
            CricketHapticEvent.CLICK -> HapticFeedbackType.TextHandleMove
        }
        performHapticFeedback(type)
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

package com.tanush.bouncer.ui.components.celebrations

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Predefined haptic patterns for cricket events
 */
object CricketHaptics {
    
    /**
     * Light tick for UI interactions
     */
    val tick = HapticPattern(
        name = "tick",
        timings = longArrayOf(10),
        amplitudes = intArrayOf(80)
    )
    
    /**
     * Boundary haptic - Four variant (medium impact)
     */
    val boundaryFour = HapticPattern(
        name = "boundaryFour",
        timings = longArrayOf(0, 50, 30, 30),
        amplitudes = intArrayOf(0, 150, 0, 100)
    )
    
    /**
     * Boundary haptic - Six variant (strong impact with celebration)
     */
    val boundarySix = HapticPattern(
        name = "boundarySix",
        timings = longArrayOf(0, 80, 40, 60, 30, 40),
        amplitudes = intArrayOf(0, 200, 0, 180, 0, 120)
    )
    
    /**
     * Wicket haptic with strong impact feel
     */
    val wicket = HapticPattern(
        name = "wicket",
        timings = longArrayOf(0, 100, 50, 80, 40, 60),
        amplitudes = intArrayOf(0, 255, 0, 200, 0, 150)
    )
    
    /**
     * Progressive haptic pattern when approaching a milestone
     */
    val milestoneApproaching = HapticPattern(
        name = "milestoneApproaching",
        timings = longArrayOf(0, 20, 80, 30, 60, 40, 40, 50),
        amplitudes = intArrayOf(0, 50, 0, 80, 0, 120, 0, 160)
    )
    
    /**
     * Century/Fifty celebration haptic
     */
    val milestoneCelebration = HapticPattern(
        name = "milestoneCelebration",
        timings = longArrayOf(0, 60, 40, 80, 30, 100, 40, 120, 50, 80),
        amplitudes = intArrayOf(0, 180, 0, 200, 0, 220, 0, 255, 0, 150)
    )
    
    /**
     * Match win celebratory pattern
     */
    val matchWin = HapticPattern(
        name = "matchWin",
        timings = longArrayOf(0, 100, 50, 80, 50, 100, 80, 150, 100, 200, 150, 100),
        amplitudes = intArrayOf(0, 180, 0, 150, 0, 200, 0, 220, 0, 255, 0, 180)
    )
    
    /**
     * Error/failure haptic feedback
     */
    val error = HapticPattern(
        name = "error",
        timings = longArrayOf(0, 60, 80, 60, 80, 60),
        amplitudes = intArrayOf(0, 180, 0, 150, 0, 120)
    )
    
    /**
     * Dot ball - subtle disappointment
     */
    val dotBall = HapticPattern(
        name = "dotBall",
        timings = longArrayOf(0, 15),
        amplitudes = intArrayOf(0, 40)
    )
    
    /**
     * Hat-trick special celebration
     */
    val hatTrick = HapticPattern(
        name = "hatTrick",
        timings = longArrayOf(0, 80, 100, 80, 100, 80, 150, 150),
        amplitudes = intArrayOf(0, 200, 0, 200, 0, 200, 0, 255)
    )
    
    /**
     * Five wicket haul celebration
     */
    val fiveWicketHaul = HapticPattern(
        name = "fiveWicketHaul",
        timings = longArrayOf(0, 50, 50, 50, 50, 50, 50, 50, 50, 50, 100, 150),
        amplitudes = intArrayOf(0, 120, 0, 140, 0, 160, 0, 180, 0, 200, 0, 255)
    )
    
    /**
     * Run out - quick sharp feedback
     */
    val runOut = HapticPattern(
        name = "runOut",
        timings = longArrayOf(0, 30, 20, 50),
        amplitudes = intArrayOf(0, 200, 0, 150)
    )
    
    /**
     * Catch taken
     */
    val catchTaken = HapticPattern(
        name = "catchTaken",
        timings = longArrayOf(0, 60, 40, 40),
        amplitudes = intArrayOf(0, 180, 0, 100)
    )
    
    /**
     * Score update pulse
     */
    val scoreUpdate = HapticPattern(
        name = "scoreUpdate",
        timings = longArrayOf(0, 25),
        amplitudes = intArrayOf(0, 100)
    )
    
    /**
     * Live match start
     */
    val matchStart = HapticPattern(
        name = "matchStart",
        timings = longArrayOf(0, 100, 100, 100, 100, 150),
        amplitudes = intArrayOf(0, 150, 0, 180, 0, 220)
    )
    
    /**
     * Innings break
     */
    val inningsBreak = HapticPattern(
        name = "inningsBreak",
        timings = longArrayOf(0, 80, 150, 80),
        amplitudes = intArrayOf(0, 150, 0, 180)
    )
}

/**
 * Data class representing a haptic pattern
 */
data class HapticPattern(
    val name: String,
    val timings: LongArray,
    val amplitudes: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as HapticPattern
        
        if (name != other.name) return false
        if (!timings.contentEquals(other.timings)) return false
        if (!amplitudes.contentEquals(other.amplitudes)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + timings.contentHashCode()
        result = 31 * result + amplitudes.contentHashCode()
        return result
    }
}

/**
 * Utility class for playing haptic effects
 */
class HapticEffectPlayer(private val context: Context) {
    
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
     * Check if haptics are supported on this device
     */
    fun isHapticsSupported(): Boolean {
        return vibrator?.hasVibrator() == true
    }
    
    /**
     * Check if amplitude control is supported
     */
    fun isAmplitudeControlSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.hasAmplitudeControl() == true
        } else {
            false
        }
    }
    
    /**
     * Play a predefined haptic pattern
     */
    fun playPattern(pattern: HapticPattern) {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vib.hasAmplitudeControl()) {
                    val effect = VibrationEffect.createWaveform(
                        pattern.timings,
                        pattern.amplitudes,
                        -1 // Don't repeat
                    )
                    vib.vibrate(effect)
                } else {
                    // Fallback for devices without amplitude control
                    val effect = VibrationEffect.createWaveform(pattern.timings, -1)
                    vib.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern.timings, -1)
            }
        }
    }
    
    /**
     * Play a simple one-shot vibration
     */
    fun playOneShot(durationMs: Long, amplitude: Int = 150) {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(
                    durationMs,
                    if (vib.hasAmplitudeControl()) amplitude.coerceIn(1, 255)
                    else VibrationEffect.DEFAULT_AMPLITUDE
                )
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }
    
    /**
     * Play a click haptic (lightweight)
     */
    fun playClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            playOneShot(10, 80)
        }
    }
    
    /**
     * Play a heavy click haptic
     */
    fun playHeavyClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            playOneShot(30, 200)
        }
    }
    
    /**
     * Play a double click haptic
     */
    fun playDoubleClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            playPattern(HapticPattern("doubleClick", longArrayOf(0, 20, 40, 20), intArrayOf(0, 150, 0, 150)))
        }
    }
    
    /**
     * Play a tick haptic (very light)
     */
    fun playTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            playOneShot(5, 50)
        }
    }
    
    /**
     * Cancel any ongoing vibration
     */
    fun cancel() {
        vibrator?.cancel()
    }
    
    // Convenience methods for cricket-specific haptics
    
    fun playBoundaryFour() = playPattern(CricketHaptics.boundaryFour)
    
    fun playBoundarySix() = playPattern(CricketHaptics.boundarySix)
    
    fun playWicket() = playPattern(CricketHaptics.wicket)
    
    fun playMilestoneApproaching() = playPattern(CricketHaptics.milestoneApproaching)
    
    fun playMilestoneCelebration() = playPattern(CricketHaptics.milestoneCelebration)
    
    fun playMatchWin() = playPattern(CricketHaptics.matchWin)
    
    fun playError() = playPattern(CricketHaptics.error)
    
    fun playDotBall() = playPattern(CricketHaptics.dotBall)
    
    fun playHatTrick() = playPattern(CricketHaptics.hatTrick)
    
    fun playFiveWicketHaul() = playPattern(CricketHaptics.fiveWicketHaul)
    
    fun playRunOut() = playPattern(CricketHaptics.runOut)
    
    fun playCatchTaken() = playPattern(CricketHaptics.catchTaken)
    
    fun playScoreUpdate() = playPattern(CricketHaptics.scoreUpdate)
    
    fun playMatchStart() = playPattern(CricketHaptics.matchStart)
    
    fun playInningsBreak() = playPattern(CricketHaptics.inningsBreak)
}

/**
 * Composable function to remember a HapticEffectPlayer instance
 */
@Composable
fun rememberHapticPlayer(): HapticEffectPlayer {
    val context = LocalContext.current
    return remember { HapticEffectPlayer(context) }
}

/**
 * Extension function to play haptic feedback based on cricket event
 */
fun HapticFeedback.performCricketHaptic(eventType: CricketHapticEvent) {
    when (eventType) {
        CricketHapticEvent.BOUNDARY_FOUR,
        CricketHapticEvent.BOUNDARY_SIX,
        CricketHapticEvent.WICKET -> performHapticFeedback(HapticFeedbackType.LongPress)
        CricketHapticEvent.MILESTONE,
        CricketHapticEvent.MATCH_WIN -> performHapticFeedback(HapticFeedbackType.LongPress)
        else -> performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

/**
 * Enum for cricket-specific haptic events
 */
enum class CricketHapticEvent {
    TICK,
    BOUNDARY_FOUR,
    BOUNDARY_SIX,
    WICKET,
    MILESTONE_APPROACHING,
    MILESTONE,
    MATCH_WIN,
    ERROR,
    DOT_BALL,
    HAT_TRICK,
    FIVE_WICKET_HAUL,
    RUN_OUT,
    CATCH_TAKEN,
    SCORE_UPDATE,
    MATCH_START,
    INNINGS_BREAK
}

/**
 * Helper to bridge HapticEffectPlayer with CricketHapticEvent
 */
fun HapticEffectPlayer.play(event: CricketHapticEvent) {
    when (event) {
        CricketHapticEvent.TICK -> playTick()
        CricketHapticEvent.BOUNDARY_FOUR -> playBoundaryFour()
        CricketHapticEvent.BOUNDARY_SIX -> playBoundarySix()
        CricketHapticEvent.WICKET -> playWicket()
        CricketHapticEvent.MILESTONE_APPROACHING -> playMilestoneApproaching()
        CricketHapticEvent.MILESTONE -> playMilestoneCelebration()
        CricketHapticEvent.MATCH_WIN -> playMatchWin()
        CricketHapticEvent.ERROR -> playError()
        CricketHapticEvent.DOT_BALL -> playDotBall()
        CricketHapticEvent.HAT_TRICK -> playHatTrick()
        CricketHapticEvent.FIVE_WICKET_HAUL -> playFiveWicketHaul()
        CricketHapticEvent.RUN_OUT -> playRunOut()
        CricketHapticEvent.CATCH_TAKEN -> playCatchTaken()
        CricketHapticEvent.SCORE_UPDATE -> playScoreUpdate()
        CricketHapticEvent.MATCH_START -> playMatchStart()
        CricketHapticEvent.INNINGS_BREAK -> playInningsBreak()
    }
}
